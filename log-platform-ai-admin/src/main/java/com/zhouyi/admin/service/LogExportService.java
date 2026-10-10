package com.zhouyi.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouyi.admin.config.LogQueryProperties;
import com.zhouyi.admin.domain.log.LogExportStatus;
import com.zhouyi.admin.domain.log.LogExportStatusResponse;
import com.zhouyi.admin.domain.log.LogSearchRequest;
import com.zhouyi.admin.domain.log.LogSearchResponse;
import com.zhouyi.admin.domain.log.ProjectAccessScope;
import com.zhouyi.common.domain.model.LoginUser;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 异步日志导出服务。
 *
 * <p>任务只保存权限快照和脱敏后的 CSV 字节，不允许绕过 {@link LogQueryService}
 * 直接提交 Elasticsearch DSL。线程池和队列均有界，避免导出请求耗尽 Web 请求线程
 * 或 JVM 内存；生产环境仍应根据真实压测结果调整行数和字节上限。</p>
 */
@Service
public class LogExportService {

    private static final List<String> CSV_HEADERS = List.of(
            "id", "index", "@timestamp", "event.id", "project.id",
            "deployment.environment", "service.name", "service.instance.id",
            "trace.id", "span.id", "log.level", "error.type", "message", "error.stack_trace");

    private final LogQueryService queryService;
    private final LogQueryProperties properties;
    private final ObjectMapper objectMapper;
    private final PlatformMetrics metrics;
    private final Map<String, ExportJob> jobs = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor;

    /** 创建有界导出线程池，防止大量导出任务拖垮查询服务。 */
    public LogExportService(LogQueryService queryService, LogQueryProperties properties,
                            ObjectMapper objectMapper, PlatformMetrics metrics) {
        this.queryService = queryService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        int workers = Math.max(1, properties.getExportWorkerThreads());
        int queueCapacity = Math.max(1, properties.getExportQueueCapacity());
        this.executor = new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), runnable -> {
                    Thread thread = new Thread(runnable, "log-export-worker");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    /** 创建任务并立即返回排队状态，查询和 CSV 生成在导出线程池内执行。 */
    public LogExportStatusResponse submit(LogSearchRequest request, ProjectAccessScope scope,
                                          Authentication authentication) {
        LoginUser owner = owner(authentication);
        validateRequest(request);
        String jobId = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        ExportJob job = new ExportJob(jobId, owner.getUserId(), owner.getUsername(), scope, request, createdAt);
        jobs.put(jobId, job);
        if (metrics != null) {
            metrics.markExportCreated();
        }
        try {
            executor.execute(() -> execute(job));
        } catch (RejectedExecutionException exception) {
            job.fail("导出任务队列已满，请稍后重试");
            if (metrics != null) {
                metrics.markExportFailed();
            }
            throw new ExportBusyException("导出任务队列已满，请稍后重试");
        }
        return job.snapshot();
    }

    /** 查询任务状态，并校验当前用户是否为任务所有者或管理员。 */
    public LogExportStatusResponse status(String jobId, Authentication authentication) {
        ExportJob job = visibleJob(jobId, authentication);
        job.expireIfNeeded(Instant.now());
        return job.snapshot();
    }

    /** 读取已完成的 CSV 内容，下载前再次执行任务所有权校验。 */
    public ExportDownload download(String jobId, Authentication authentication) {
        ExportJob job = visibleJob(jobId, authentication);
        job.expireIfNeeded(Instant.now());
        LogExportStatus status = job.status.get();
        if (status == LogExportStatus.EXPIRED) {
            throw new ExportExpiredException("导出结果已过期");
        }
        if (status != LogExportStatus.SUCCEEDED || job.content == null) {
            throw new ExportNotReadyException("导出任务尚未完成");
        }
        return new ExportDownload(job.content, "logs-export-" + job.jobId + ".csv");
    }

    /** 定期把过期任务标记为 EXPIRED，并清理额外保留一个周期后的内存内容。 */
    @Scheduled(fixedDelayString = "${log-platform.query.export-cleanup-interval-ms:60000}")
    public void cleanupExpiredJobs() {
        Instant now = Instant.now();
        jobs.entrySet().removeIf(entry -> entry.getValue().purgeIfNeeded(now));
    }

    /** 关闭应用时停止导出线程，避免非守护任务阻止进程退出。 */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    /** 在后台线程执行单个导出任务，并将异常转换为可展示的失败状态。 */
    private void execute(ExportJob job) {
        job.runningAt = Instant.now();
        job.status.set(LogExportStatus.RUNNING);
        try {
            ExportResult result = buildCsv(job.request, job.scope);
            job.succeed(result.content(), result.rowCount(), Instant.now());
        } catch (Exception exception) {
            String message = userMessage(exception);
            job.fail(message);
            if (metrics != null) {
                metrics.markExportFailed();
            }
        }
    }

    /** 分页读取脱敏日志并生成受大小限制的 UTF-8 CSV。 */
    private ExportResult buildCsv(LogSearchRequest request, ProjectAccessScope scope) {
        int pageSize = request.size() == null ? Math.min(500, properties.getMaxPageSize()) : request.size();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            // UTF-8 BOM 方便 Excel 识别中文，不改变 CSV 字段内容。
            writer.write('\uFEFF');
            writeCsvRow(writer, CSV_HEADERS);
            List<Object> cursor = List.of();
            long rows = 0;
            while (rows < properties.getExportMaxRows()) {
                LogSearchResponse page = queryService.search(withPage(request, pageSize, cursor), scope);
                for (LogSearchResponse.LogHit hit : page.items()) {
                    if (rows >= properties.getExportMaxRows()) {
                        break;
                    }
                    writeCsvRow(writer, values(hit));
                    rows++;
                    writer.flush();
                    ensureBytesWithinLimit(output.size());
                }
                List<Object> next = page.nextSearchAfter() == null ? List.of() : page.nextSearchAfter();
                if (rows >= properties.getExportMaxRows() || next.isEmpty()) {
                    return new ExportResult(output.toByteArray(), rows);
                }
                if (next.equals(cursor)) {
                    throw new IllegalStateException("Elasticsearch 分页游标未前进");
                }
                cursor = List.copyOf(next);
            }
            writer.flush();
            return new ExportResult(output.toByteArray(), rows);
        } catch (IOException exception) {
            throw new UncheckedIOException("生成 CSV 失败", exception);
        }
    }

    /** 复制查询条件并替换页大小和 search_after，保证导出不修改调用方对象。 */
    private LogSearchRequest withPage(LogSearchRequest request, int pageSize, List<Object> cursor) {
        return new LogSearchRequest(request.from(), request.to(), request.projectId(),
                request.deploymentEnvironment(), request.serviceName(), request.serviceInstanceId(),
                request.traceId(), request.spanId(), request.logLevel(), request.errorType(),
                request.keyword(), request.attributes(), pageSize, cursor);
    }

    /** 将一条脱敏日志映射为固定顺序的 CSV 字段。 */
    private List<String> values(LogSearchResponse.LogHit hit) {
        JsonNode source = hit.source();
        return List.of(safe(hit.id()), safe(hit.index()), text(source, "@timestamp"), text(source, "event.id"),
                text(source, "project.id"), text(source, "deployment.environment"),
                text(source, "service.name"), text(source, "service.instance.id"),
                text(source, "trace.id"), text(source, "span.id"), text(source, "log.level"),
                text(source, "error.type"), text(source, "message"), text(source, "error.stack_trace"));
    }

    /** 将异常或缺失的 ES 元数据转换为空字符串，保证 CSV 行不会因 List.of 拒绝 null。 */
    /** 将缺失的 ES 元数据转换为空字符串。 */
    private String safe(String value) {
        return value == null ? "" : value;
    }

    /** 读取标量字段或把对象字段序列化为单个 CSV 单元格。 */
    private String text(JsonNode source, String field) {
        if (source == null || source.get(field) == null || source.get(field).isNull()) {
            return "";
        }
        JsonNode value = source.get(field);
        if (value.isValueNode()) {
            return value.asText();
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return value.toString();
        }
    }

    /** 按 RFC 4180 规则写出一行并统一使用 CRLF 换行。 */
    private void writeCsvRow(OutputStreamWriter writer, List<String> values) throws IOException {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                writer.write(',');
            }
            writer.write(escapeCsv(values.get(index)));
        }
        writer.write("\r\n");
    }

    /** 转义 CSV 特殊字符并阻止常见电子表格公式注入。 */
    private String escapeCsv(String value) {
        String text = value == null ? "" : value;
        // 防止导入 Excel 时把日志内容解释为公式。
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.indexOf('"') >= 0 || text.indexOf(',') >= 0
                || text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    /** 检查生成结果是否超过配置的内存保护上限。 */
    private void ensureBytesWithinLimit(int bytes) {
        if (bytes > Math.max(1024, properties.getExportMaxBytes())) {
            throw new ExportLimitException("导出结果超过字节数限制");
        }
    }

    /** 在排队前校验导出请求，避免非法任务占用异步队列。 */
    private void validateRequest(LogSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("导出查询条件不能为空");
        }
        if (request.searchAfter() != null && !request.searchAfter().isEmpty()) {
            throw new IllegalArgumentException("导出不支持从 searchAfter 游标开始");
        }
        Integer size = request.size();
        if (size != null && (size < 1 || size > properties.getMaxPageSize())) {
            throw new IllegalArgumentException("size 必须在 1 到 " + properties.getMaxPageSize() + " 之间");
        }
        if (properties.getExportMaxRows() < 1 || properties.getExportMaxRows() > 10000) {
            throw new IllegalArgumentException("导出行数上限必须在 1 到 10000 之间");
        }
    }

    /** 加载任务并执行所有者或管理员权限校验。 */
    private ExportJob visibleJob(String jobId, Authentication authentication) {
        if (jobId == null || jobId.isBlank() || jobId.length() > 64) {
            throw new IllegalArgumentException("导出任务 ID 无效");
        }
        ExportJob job = jobs.get(jobId);
        if (job == null) {
            throw new ExportNotFoundException("导出任务不存在");
        }
        LoginUser owner = owner(authentication);
        if (!owner.isAdmin() && !owner.getUserId().equals(job.ownerUserId)) {
            throw new AccessDeniedException("无权访问该导出任务");
        }
        return job;
    }

    /** 从服务端认证对象中提取登录用户，拒绝匿名任务。 */
    private LoginUser owner(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser user)) {
            throw new AccessDeniedException("无法识别当前用户");
        }
        return user;
    }

    /** 将内部异常归一化为不泄露堆栈的用户可读消息。 */
    private String userMessage(Exception exception) {
        if (exception instanceof AccessDeniedException) {
            return exception.getMessage();
        }
        if (exception instanceof ExportLimitException) {
            return exception.getMessage();
        }
        if (exception instanceof LogQueryService.QueryTimeoutException) {
            return "日志查询超时，请缩小时间范围后重试";
        }
        return exception.getMessage() == null ? "导出失败" : exception.getMessage();
    }

    /** 下载响应载荷，避免 Controller 依赖内部任务对象。 */
    public record ExportDownload(
            /** CSV 文件字节内容。 */
            byte[] content,
            /** 下载文件名。 */
            String filename) {
    }

    /** 后台生成 CSV 的内部结果。 */
    private record ExportResult(
            /** CSV 文件字节内容。 */
            byte[] content,
            /** 已写入的日志行数。 */
            long rowCount) {
    }

    private final class ExportJob {
        private final String jobId;
        private final Long ownerUserId;
        private final String owner;
        private final ProjectAccessScope scope;
        private final LogSearchRequest request;
        private final Instant createdAt;
        private final AtomicReference<LogExportStatus> status = new AtomicReference<>(LogExportStatus.QUEUED);
        private volatile Instant runningAt;
        private volatile Instant finishedAt;
        private volatile Instant expiresAt;
        private volatile long rowCount;
        private volatile String error;
        private volatile byte[] content;

        private ExportJob(String jobId, Long ownerUserId, String owner, ProjectAccessScope scope,
                          LogSearchRequest request, Instant createdAt) {
            this.jobId = jobId;
            this.ownerUserId = ownerUserId;
            this.owner = owner;
            this.scope = scope;
            this.request = request;
            this.createdAt = createdAt;
        }

        private void succeed(byte[] content, long rowCount, Instant finishedAt) {
            this.content = content;
            this.rowCount = rowCount;
            this.finishedAt = finishedAt;
            this.expiresAt = finishedAt.plus(properties.getExportRetentionMinutes(), ChronoUnit.MINUTES);
            this.status.set(LogExportStatus.SUCCEEDED);
        }

        private void fail(String error) {
            this.error = error;
            this.finishedAt = Instant.now();
            this.expiresAt = finishedAt.plus(properties.getExportRetentionMinutes(), ChronoUnit.MINUTES);
            this.status.set(LogExportStatus.FAILED);
        }

        private void expireIfNeeded(Instant now) {
            if (expiresAt != null && !now.isBefore(expiresAt)
                    && status.compareAndSet(LogExportStatus.SUCCEEDED, LogExportStatus.EXPIRED)) {
                content = null;
            }
        }

        private boolean purgeIfNeeded(Instant now) {
            expireIfNeeded(now);
            return expiresAt != null && now.isAfter(expiresAt.plus(1, ChronoUnit.MINUTES));
        }

        private LogExportStatusResponse snapshot() {
            LogExportStatus current = status.get();
            return new LogExportStatusResponse(jobId, current, owner, rowCount, createdAt, runningAt,
                    finishedAt, expiresAt, error,
                    current == LogExportStatus.SUCCEEDED ? "/monitor/api/logs/exports/" + jobId + "/download" : null);
        }
    }

    /** 导出队列满时由 Controller 转换为 429。 */
    public static final class ExportBusyException extends RuntimeException {
        public ExportBusyException(String message) { super(message); }
    }

    /** 任务完成但结果已过期。 */
    public static final class ExportExpiredException extends RuntimeException {
        public ExportExpiredException(String message) { super(message); }
    }

    /** 任务尚未完成或已失败。 */
    public static final class ExportNotReadyException extends RuntimeException {
        public ExportNotReadyException(String message) { super(message); }
    }

    /** 任务不存在。 */
    public static final class ExportNotFoundException extends RuntimeException {
        public ExportNotFoundException(String message) { super(message); }
    }

    /** 导出内容超过服务端硬上限。 */
    public static final class ExportLimitException extends RuntimeException {
        public ExportLimitException(String message) { super(message); }
    }
}
