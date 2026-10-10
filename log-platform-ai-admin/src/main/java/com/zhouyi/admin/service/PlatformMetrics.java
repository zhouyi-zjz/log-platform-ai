package com.zhouyi.admin.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 日志平台核心链路指标。
 *
 * <p>指标名采用稳定的业务语义，避免把项目 ID、用户名等高基数值写入标签；
 * 项目维度如需分析应使用审计表或后续日志聚合，而不是直接扩大 Prometheus 标签基数。</p>
 */
@Component
public class PlatformMetrics {

    private final Counter ingestAccepted;
    private final Counter ingestRejected;
    private final Counter consumerEvents;
    private final Counter consumerBatches;
    private final Counter bulkFailures;
    private final Counter bulkDuplicates;
    private final Counter exportCreated;
    private final Counter exportFailed;
    private final Counter dltReplaySuccess;
    private final Counter dltReplayFailure;
    private final Timer ingestAckLatency;
    private final Timer queryLatency;
    private final Timer bulkLatency;
    private final AtomicLong consumerLag = new AtomicLong(-1);
    private final AtomicLong dltPending = new AtomicLong(0);

    /** 注册平台固定指标，启动时即能被 Actuator/Prometheus 发现。 */
    public PlatformMetrics(MeterRegistry registry) {
        ingestAccepted = Counter.builder("log_platform_ingest_accepted_total")
                .description("Kafka 已确认的 HTTP 日志接入数").register(registry);
        ingestRejected = Counter.builder("log_platform_ingest_rejected_total")
                .description("被拒绝或发布失败的 HTTP 日志接入数").register(registry);
        consumerEvents = Counter.builder("log_platform_consumer_events_total")
                .description("Kafka 消费的日志事件数").register(registry);
        consumerBatches = Counter.builder("log_platform_consumer_batches_total")
                .description("Kafka 消费批次数").register(registry);
        bulkFailures = Counter.builder("log_platform_bulk_failures_total")
                .description("Elasticsearch Bulk 失败批次数").register(registry);
        bulkDuplicates = Counter.builder("log_platform_bulk_duplicates_total")
                .description("Elasticsearch Bulk 幂等冲突数").register(registry);
        exportCreated = Counter.builder("log_platform_export_created_total")
                .description("创建的异步导出任务数").register(registry);
        exportFailed = Counter.builder("log_platform_export_failed_total")
                .description("失败的异步导出任务数").register(registry);
        dltReplaySuccess = Counter.builder("log_platform_dlt_replay_success_total")
                .description("成功重放的 DLT 消息数").register(registry);
        dltReplayFailure = Counter.builder("log_platform_dlt_replay_failure_total")
                .description("失败的 DLT 重放尝试数").register(registry);
        ingestAckLatency = Timer.builder("log_platform_ingest_ack_latency")
                .description("HTTP 接入等待 Kafka ack 的耗时").register(registry);
        queryLatency = Timer.builder("log_platform_query_latency")
                .description("日志查询服务耗时").register(registry);
        bulkLatency = Timer.builder("log_platform_bulk_latency")
                .description("Elasticsearch Bulk 请求耗时").register(registry);
        registry.gauge("log_platform_consumer_lag", consumerLag);
        registry.gauge("log_platform_dlt_pending_records", dltPending);
    }

    /** 记录一次 Kafka 已确认的接入。 */
    public void markIngestAccepted() { ingestAccepted.increment(); }

    /** 记录一次入口拒绝或 Kafka 发布失败。 */
    public void markIngestRejected() { ingestRejected.increment(); }

    /** 记录批次和事件数量。 */
    public void markConsumed(int eventCount) {
        consumerBatches.increment();
        consumerEvents.increment(Math.max(0, eventCount));
    }

    /** 记录 Bulk 失败批次。 */
    public void markBulkFailure() { bulkFailures.increment(); }

    /** 记录 Bulk 中被识别为幂等成功的 409 数量。 */
    public void markBulkDuplicates(long count) { bulkDuplicates.increment(Math.max(0, count)); }

    /** 记录创建的异步导出任务。 */
    public void markExportCreated() { exportCreated.increment(); }

    /** 记录失败的异步导出任务。 */
    public void markExportFailed() { exportFailed.increment(); }

    /** 记录 DLT 单条重放成功。 */
    public void markDltReplaySuccess() { dltReplaySuccess.increment(); }

    /** 记录 DLT 单条重放失败。 */
    public void markDltReplayFailure() { dltReplayFailure.increment(); }

    /** 创建入口 ack 计时样本。 */
    public long startIngestAck() { return System.nanoTime(); }

    /** 创建查询计时样本。 */
    public long startQuery() { return System.nanoTime(); }

    /** 创建 ES Bulk 计时样本。 */
    public long startBulk() { return System.nanoTime(); }

    /** 记录入口 ack 样本。 */
    public void recordIngestAck(long startedNanos) { ingestAckLatency.record(elapsedNanos(startedNanos), TimeUnit.NANOSECONDS); }

    /** 记录查询样本。 */
    public void recordQuery(long startedNanos) { queryLatency.record(elapsedNanos(startedNanos), TimeUnit.NANOSECONDS); }

    /** 记录 ES Bulk 样本。 */
    public void recordBulk(long startedNanos) { bulkLatency.record(elapsedNanos(startedNanos), TimeUnit.NANOSECONDS); }

    /** 计算耗时并处理系统时钟异常，保证指标不会出现负数。 */
    private static long elapsedNanos(long startedNanos) {
        return Math.max(0, System.nanoTime() - startedNanos);
    }

    /** 更新最近一次已知的消费积压；未知值使用 -1 表示。 */
    public void setConsumerLag(long lag) { consumerLag.set(lag); }

    /** 更新允许操作的 DLT 总积压，未知时使用 -1。 */
    public void setDltPending(long pending) { dltPending.set(Math.max(-1, pending)); }
}
