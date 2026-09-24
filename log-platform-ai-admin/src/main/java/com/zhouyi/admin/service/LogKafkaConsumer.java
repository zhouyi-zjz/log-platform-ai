package com.zhouyi.admin.service;

import com.zhouyi.common.domain.log.LogEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
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

    public LogKafkaConsumer(LogEventNormalizer normalizer, ElasticsearchLogWriter logWriter) {
        this.normalizer = normalizer;
        this.logWriter = logWriter;
    }

    @KafkaListener(
            topics = "${log-platform.ingest.kafka-topic:logs.raw}",
            groupId = "${log-platform.ingest.consumer-group:logs-es-writer}",
            containerFactory = "logKafkaListenerContainerFactory",
            autoStartup = "${log-platform.ingest.consumer-enabled:false}")
    public void consume(List<ConsumerRecord<String, String>> records) {
        List<LogEvent> events = records.stream()
                .map(record -> normalizer.normalize(record.value()))
                .toList();
        // 只有 ES Bulk 成功返回后，监听容器才会提交这一批 Kafka offset。
        logWriter.writeBulk(events);
    }
}
