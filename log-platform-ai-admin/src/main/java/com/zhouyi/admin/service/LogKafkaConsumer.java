package com.zhouyi.admin.service;

import com.zhouyi.common.domain.log.LogEvent;
import com.zhouyi.common.domain.telemetry.TelemetryContext;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 *
 * @author zjz
 * @version 1.0
 * @description: Kafka 到 Elasticsearch 的规范化消费端。
 * @date 2026/9/12
 */
@Service
public class LogKafkaConsumer {

    private final LogEventNormalizer normalizer;
    private final ElasticsearchLogWriter logWriter;
    private final PlatformMetrics metrics;
    private final KafkaTracePropagation tracePropagation;
    private final TraceCompletenessService completenessService;

    public LogKafkaConsumer(LogEventNormalizer normalizer, ElasticsearchLogWriter logWriter) {
        this(normalizer, logWriter, null, null, null);
    }

    /** 创建带消费批次埋点的 Kafka 消费服务。 */
    @Autowired
    public LogKafkaConsumer(LogEventNormalizer normalizer, ElasticsearchLogWriter logWriter,
                            PlatformMetrics metrics, KafkaTracePropagation tracePropagation,
                            TraceCompletenessService completenessService) {
        this.normalizer = normalizer;
        this.logWriter = logWriter;
        this.metrics = metrics;
        this.tracePropagation = tracePropagation;
        this.completenessService = completenessService;
    }

    /** 批量归一化并写入 ES；方法正常返回后由监听容器提交 Kafka offset。 */
    @KafkaListener(
            topics = "${log-platform.ingest.kafka-topic:logs.raw}",
            groupId = "${log-platform.ingest.consumer-group:logs-es-writer}",
            containerFactory = "logKafkaListenerContainerFactory",
            autoStartup = "${log-platform.ingest.consumer-enabled:false}")
    public void consume(List<ConsumerRecord<String, String>> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<LogEvent> events = records.stream()
                .map(record -> normalizeRecord(record))
                .toList();
        if (metrics != null) {
            metrics.markConsumed(events.size());
        }
        // 只有 ES Bulk 成功返回后，监听容器才会提交这一批 Kafka offset。
        logWriter.writeBulk(events);
    }

    /** 按消息 Header 提取上下文后归一化单条日志，传播异常按缺失链路处理。 */
    private LogEvent normalizeRecord(ConsumerRecord<String, String> record) {
        TelemetryContext context = tracePropagation == null ? null : tracePropagation.extract(record);
        LogEvent event = normalizer.normalize(record.value(), kafkaEventId(record), context);
        if (completenessService != null) {
            completenessService.inspect(event);
        }
        return event;
    }

    private static String kafkaEventId(ConsumerRecord<String, String> record) {
        return "kafka:" + record.topic() + ":" + record.partition() + ":" + record.offset();
    }
}
