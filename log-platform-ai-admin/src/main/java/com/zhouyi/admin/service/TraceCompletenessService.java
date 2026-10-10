package com.zhouyi.admin.service;

import com.zhouyi.common.domain.log.LogEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Trace 完整率巡检器。
 *
 * <p>它只检查受控的关联元数据，不读取日志正文，也不把 Trace ID 写入 Prometheus 标签。
 * 缺失字段使用固定 Counter 分支记录，避免动态高基数标签；查询接口只返回聚合快照。</p>
 */
@Service
public class TraceCompletenessService {

    private final Counter inspected;
    private final Counter complete;
    private final Counter missingTrace;
    private final Counter missingSpan;
    private final Counter missingRequest;
    private final AtomicLong inspectedCount = new AtomicLong();
    private final AtomicLong completeCount = new AtomicLong();
    private final AtomicLong missingTraceCount = new AtomicLong();
    private final AtomicLong missingSpanCount = new AtomicLong();
    private final AtomicLong missingRequestCount = new AtomicLong();

    /** 注册低基数完整率指标。 */
    public TraceCompletenessService(MeterRegistry registry) {
        inspected = Counter.builder("log_platform_trace_completeness_inspected_total")
                .description("参与 Trace 完整率检查的日志事件数").register(registry);
        complete = Counter.builder("log_platform_trace_completeness_complete_total")
                .description("同时包含 trace/span/request 关联字段的日志事件数").register(registry);
        missingTrace = Counter.builder("log_platform_trace_completeness_missing_trace_total")
                .description("缺少 trace.id 的日志事件数").register(registry);
        missingSpan = Counter.builder("log_platform_trace_completeness_missing_span_total")
                .description("缺少 span.id 的日志事件数").register(registry);
        missingRequest = Counter.builder("log_platform_trace_completeness_missing_request_total")
                .description("缺少 request.id 的日志事件数").register(registry);
    }

    /** 检查单条事件并更新聚合指标。 */
    public void inspect(LogEvent event) {
        if (event == null) {
            return;
        }
        boolean trace = present(event.traceId());
        boolean span = present(event.spanId());
        boolean request = present(event.requestId());
        // 未启用链路追踪的普通日志不进入 Trace 完整率分母，避免把日志平台自身的普通日志误报为链路故障。
        if (!trace && !span && !request) {
            return;
        }
        inspected.increment();
        inspectedCount.incrementAndGet();
        if (trace && span && request) {
            complete.increment();
            completeCount.incrementAndGet();
        }
        if (!trace) {
            missingTrace.increment();
            missingTraceCount.incrementAndGet();
        }
        if (!span) {
            missingSpan.increment();
            missingSpanCount.incrementAndGet();
        }
        if (!request) {
            missingRequest.increment();
            missingRequestCount.incrementAndGet();
        }
    }

    /** 返回当前进程的完整率聚合快照，适用于管理员巡检和故障诊断。 */
    public Snapshot snapshot() {
        long total = inspectedCount.get();
        long completed = completeCount.get();
        double rate = total == 0 ? 1.0 : (double) completed / total;
        Map<String, Long> missing = new LinkedHashMap<>();
        missing.put("trace.id", missingTraceCount.get());
        missing.put("span.id", missingSpanCount.get());
        missing.put("request.id", missingRequestCount.get());
        return new Snapshot(total, completed, rate, missing);
    }

    /** 判断关联字段是否非空。 */
    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    /** Trace 完整率快照，不包含任意单条日志或高基数 ID。 */
    public record Snapshot(long inspected, long complete, double completenessRate,
                           Map<String, Long> missingByField) {
    }
}
