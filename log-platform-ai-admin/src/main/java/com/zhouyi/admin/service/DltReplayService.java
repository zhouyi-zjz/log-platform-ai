package com.zhouyi.admin.service;

import com.zhouyi.admin.config.FinopsIntegrationProperties;
import com.zhouyi.admin.config.LogIngestProperties;
import com.zhouyi.admin.domain.log.DltReplayRequest;
import com.zhouyi.admin.domain.log.DltTopicStatus;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 提供受限、可审计、单条消息级别的 DLT 查看和重放能力。 */
@Service
public class DltReplayService {

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(3);
    private final ConsumerFactory<String, String> consumerFactory;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final LogIngestProperties logProperties;
    private final FinopsIntegrationProperties finopsProperties;
    private final LogQueryAuditService auditService;
    private final PlatformMetrics metrics;

    /** 创建 DLT 运维服务。 */
    public DltReplayService(ConsumerFactory<String, String> consumerFactory,
                            KafkaTemplate<String, String> kafkaTemplate,
                            LogIngestProperties logProperties,
                            FinopsIntegrationProperties finopsProperties,
                            LogQueryAuditService auditService,
                            PlatformMetrics metrics) {
        this.consumerFactory = consumerFactory;
        this.kafkaTemplate = kafkaTemplate;
        this.logProperties = logProperties;
        this.finopsProperties = finopsProperties;
        this.auditService = auditService;
        this.metrics = metrics;
    }

    /** 返回允许操作的 DLT Topic 状态，不读取消息正文。 */
    public List<DltTopicStatus> statuses() {
        List<DltTopicStatus> result = new ArrayList<>();
        for (String dltTopic : allowedDltTopics()) {
            result.add(status(dltTopic));
        }
        metrics.setDltPending(result.stream().flatMap(status -> status.partitions().stream())
                .mapToLong(DltTopicStatus.PartitionStatus::pendingRecords).sum());
        return result;
    }

    /** 重放单条 DLT 消息到固定的原始 Topic，并记录管理员审计。 */
    public ReplayResult replay(DltReplayRequest request, Authentication authentication) {
        validate(request);
        String targetTopic = targetTopic(request.topic());
        ConsumerRecord<String, String> source = readOne(request.topic(), request.partition(), request.offset());
        ProducerRecord<String, String> target = new ProducerRecord<>(targetTopic, source.key(), source.value());
        source.headers().forEach(header -> target.headers().add(header.key(), header.value()));
        try {
            var metadata = kafkaTemplate.send(target).get(5, TimeUnit.SECONDS).getRecordMetadata();
            String reason = request.reason() == null || request.reason().isBlank()
                    ? "DLT 单条安全重放" : request.reason().trim();
            auditService.record(authentication, "dlt_replay", "SUCCESS", null, null,
                    request.topic() + ":" + request.partition() + ":" + request.offset() + " -> " + reason);
            metrics.markDltReplaySuccess();
            return new ReplayResult(request.topic(), targetTopic, request.partition(), request.offset(),
                    metadata.partition(), metadata.offset());
        } catch (Exception exception) {
            metrics.markDltReplayFailure();
            auditService.record(authentication, "dlt_replay", "FAILED", null, null,
                    request.topic() + ":" + request.partition() + ":" + request.offset());
            throw new IllegalStateException("DLT 重放发布失败", exception);
        }
    }

    /** 读取指定 offset 的一条消息，不提交消费位点。 */
    private ConsumerRecord<String, String> readOne(String topic, int partition, long offset) {
        try (Consumer<String, String> consumer = consumerFactory.createConsumer(
                "log-platform-dlt-replay-" + UUID.randomUUID())) {
            TopicPartition topicPartition = new TopicPartition(topic, partition);
            consumer.assign(List.of(topicPartition));
            consumer.seek(topicPartition, offset);
            ConsumerRecords<String, String> records = consumer.poll(POLL_TIMEOUT);
            for (ConsumerRecord<String, String> record : records.records(topicPartition)) {
                if (record.offset() == offset) {
                    return record;
                }
            }
            throw new IllegalArgumentException("指定 DLT offset 不存在或尚未可读");
        }
    }

    /** 获取固定 DLT Topic 的各分区积压。 */
    private DltTopicStatus status(String topic) {
        try (Consumer<String, String> consumer = consumerFactory.createConsumer(
                "log-platform-dlt-status-" + UUID.randomUUID())) {
            List<org.apache.kafka.common.PartitionInfo> infos = consumer.partitionsFor(topic, POLL_TIMEOUT);
            if (infos == null) {
                return new DltTopicStatus(topic, targetTopic(topic), List.of());
            }
            List<TopicPartition> partitions = infos.stream()
                    .map(info -> new TopicPartition(topic, info.partition())).toList();
            var beginning = consumer.beginningOffsets(partitions);
            var ending = consumer.endOffsets(partitions);
            List<DltTopicStatus.PartitionStatus> result = new ArrayList<>();
            for (TopicPartition partition : partitions) {
                long start = beginning.getOrDefault(partition, 0L);
                long end = ending.getOrDefault(partition, start);
                result.add(new DltTopicStatus.PartitionStatus(partition.partition(), start, end,
                        Math.max(0, end - start)));
            }
            return new DltTopicStatus(topic, targetTopic(topic), result);
        }
    }

    /** 校验请求并阻止跨 Topic、批量和负 offset 操作。 */
    private void validate(DltReplayRequest request) {
        if (request == null || !allowedDltTopics().contains(request.topic())) {
            throw new IllegalArgumentException("不允许操作该 DLT Topic");
        }
        if (request.partition() < 0 || request.offset() < 0) {
            throw new IllegalArgumentException("partition 和 offset 必须为非负数");
        }
    }

    /** 将 DLT Topic 映射到固定的原始 Topic。 */
    private String targetTopic(String dltTopic) {
        return dltTopic.substring(0, dltTopic.length() - ".DLT".length());
    }

    /** 返回当前平台允许查看和重放的 DLT 集合。 */
    private Set<String> allowedDltTopics() {
        Set<String> topics = new LinkedHashSet<>();
        topics.add(logProperties.getKafkaTopic() + ".DLT");
        topics.add(finopsProperties.getBudgetAuditTopic() + ".DLT");
        return topics;
    }

    /** DLT 重放结果，不返回消息正文。 */
    public record ReplayResult(String sourceTopic, String targetTopic, int sourcePartition,
                               long sourceOffset, int targetPartition, long targetOffset) {
    }
}
