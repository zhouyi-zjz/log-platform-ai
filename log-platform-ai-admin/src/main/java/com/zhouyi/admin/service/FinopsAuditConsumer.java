package com.zhouyi.admin.service;

import com.zhouyi.admin.config.FinopsIntegrationProperties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

/** 消费 FinOps 预算审计事件并写入独立只读投影，不参与预算裁决。 */
@Service
public class FinopsAuditConsumer {

    private final BudgetAuditNormalizer normalizer;
    private final FinopsAuditWriter writer;
    private final FinopsIntegrationProperties properties;

    /** 创建预算审计消费者。 */
    public FinopsAuditConsumer(BudgetAuditNormalizer normalizer,
                               FinopsAuditWriter writer,
                               FinopsIntegrationProperties properties) {
        this.normalizer = normalizer;
        this.writer = writer;
        this.properties = properties;
    }

    /** 批量消费预算审计事件；任一事件失败则交给 Kafka DLT，避免静默丢失。 */
    @KafkaListener(
            topics = "${log-platform.finops.budget-audit-topic:finops.budget-audit.v1}",
            groupId = "${log-platform.finops.budget-audit-consumer-group:log-platform-budget-audit}",
            containerFactory = "logKafkaListenerContainerFactory",
            autoStartup = "${log-platform.finops.budget-audit-consumer-enabled:false}")
    public void consume(List<ConsumerRecord<String, String>> records) {
        if (!properties.isBudgetAuditConsumerEnabled() || records == null) {
            return;
        }
        for (ConsumerRecord<String, String> record : records) {
            if (record.value() == null || record.value().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                    > properties.getBudgetAuditMaxPayloadBytes()) {
                throw new IllegalArgumentException("预算审计事件超过最大字节数");
            }
            writer.write(normalizer.normalize(record.value()));
        }
    }
}
