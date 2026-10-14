package com.zhouyi.admin.service;

import com.zhouyi.admin.config.KafkaMonitoringProperties;
import com.zhouyi.admin.config.LogIngestProperties;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** 周期采集日志平台自身依赖的 Kafka 集群、Topic、消费组和 DLT 状态。 */
@Component
@ConditionalOnProperty(prefix = "log-platform.kafka-monitoring", name = "enabled", havingValue = "true")
public class KafkaPlatformMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaPlatformMonitor.class);

    private final Admin admin;
    private final KafkaMonitoringProperties monitoringProperties;
    private final LogIngestProperties ingestProperties;
    private final PlatformMetrics metrics;

    public KafkaPlatformMonitor(Admin admin,
                                KafkaMonitoringProperties monitoringProperties,
                                LogIngestProperties ingestProperties,
                                PlatformMetrics metrics) {
        this.admin = admin;
        this.monitoringProperties = monitoringProperties;
        this.ingestProperties = ingestProperties;
        this.metrics = metrics;
    }

    /** 首次启动留出 listener 初始化时间，之后按固定间隔刷新平台 Kafka 指标。 */
    @Scheduled(
            initialDelayString = "${log-platform.kafka-monitoring.initial-delay-ms:5000}",
            fixedDelayString = "${log-platform.kafka-monitoring.interval-ms:30000}")
    public void refresh() {
        try {
            KafkaSnapshot snapshot = readSnapshot();
            metrics.updateKafkaSnapshot(snapshot.brokerCount(),
                    Math.max(1, monitoringProperties.getExpectedBrokers()),
                    snapshot.underReplicatedPartitions(), snapshot.offlinePartitions(),
                    snapshot.consumerLag(), snapshot.dltPendingRecords());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            metrics.markKafkaMonitorFailure();
            LOGGER.warn("Kafka 自监控采集被中断");
        } catch (Exception exception) {
            metrics.markKafkaMonitorFailure();
            LOGGER.warn("Kafka 自监控采集失败: {}", exception.getMessage());
        }
    }

    private KafkaSnapshot readSnapshot() throws Exception {
        int brokerCount = await(admin.describeCluster().nodes()).size();
        String topic = ingestProperties.getKafkaTopic();
        String dltTopic = topic + ".DLT";
        Map<String, TopicDescription> descriptions = await(admin.describeTopics(Set.of(topic, dltTopic)).all());

        Set<TopicPartition> allPartitions = new LinkedHashSet<>();
        descriptions.forEach((name, description) -> description.partitions().forEach(
                partition -> allPartitions.add(new TopicPartition(name, partition.partition()))));
        Map<TopicPartition, Long> beginnings = offsets(allPartitions, OffsetSpec.earliest());
        Map<TopicPartition, Long> endings = offsets(allPartitions, OffsetSpec.latest());
        Map<TopicPartition, OffsetAndMetadata> committed = await(admin
                .listConsumerGroupOffsets(ingestProperties.getConsumerGroup())
                .partitionsToOffsetAndMetadata());
        return calculateSnapshot(brokerCount, descriptions.values(), topic, dltTopic,
                beginnings, endings, committed);
    }

    private Map<TopicPartition, Long> offsets(Set<TopicPartition> partitions, OffsetSpec offsetSpec)
            throws Exception {
        Map<TopicPartition, OffsetSpec> request = new LinkedHashMap<>();
        partitions.forEach(partition -> request.put(partition, offsetSpec));
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> result =
                await(admin.listOffsets(request).all());
        Map<TopicPartition, Long> offsets = new LinkedHashMap<>();
        result.forEach((partition, info) -> offsets.put(partition, info.offset()));
        return offsets;
    }

    private <T> T await(KafkaFuture<T> future) throws Exception {
        return future.get(Math.max(1000, monitoringProperties.getTimeoutMs()), TimeUnit.MILLISECONDS);
    }

    /** 将 Kafka 元数据快照转换为固定低基数指标，便于不依赖 broker JMX 先落地告警。 */
    static KafkaSnapshot calculateSnapshot(int brokerCount,
                                           Collection<TopicDescription> descriptions,
                                           String topic,
                                           String dltTopic,
                                           Map<TopicPartition, Long> beginnings,
                                           Map<TopicPartition, Long> endings,
                                           Map<TopicPartition, OffsetAndMetadata> committed) {
        long underReplicated = 0;
        long offline = 0;
        long consumerLag = 0;
        long dltPending = 0;
        for (TopicDescription description : descriptions) {
            for (TopicPartitionInfo info : description.partitions()) {
                TopicPartition partition = new TopicPartition(description.name(), info.partition());
                if (info.leader() == null || info.leader().isEmpty()) {
                    offline++;
                }
                if (info.isr().size() < info.replicas().size()) {
                    underReplicated++;
                }
                long beginning = beginnings.getOrDefault(partition, 0L);
                long end = endings.getOrDefault(partition, beginning);
                if (description.name().equals(topic)) {
                    OffsetAndMetadata offset = committed.get(partition);
                    long current = offset == null ? beginning : Math.max(beginning, offset.offset());
                    consumerLag += Math.max(0, end - current);
                } else if (description.name().equals(dltTopic)) {
                    dltPending += Math.max(0, end - beginning);
                }
            }
        }
        return new KafkaSnapshot(Math.max(0, brokerCount), underReplicated, offline, consumerLag, dltPending);
    }

    record KafkaSnapshot(int brokerCount, long underReplicatedPartitions, long offlinePartitions,
                         long consumerLag, long dltPendingRecords) {
    }
}
