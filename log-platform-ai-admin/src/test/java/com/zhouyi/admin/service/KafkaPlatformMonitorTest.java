package com.zhouyi.admin.service;

import com.zhouyi.admin.config.KafkaMonitoringProperties;
import com.zhouyi.admin.config.LogIngestProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaPlatformMonitorTest {

    @Test
    void calculatesClusterHealthLagAndDltBacklog() {
        Node broker1 = new Node(1, "kafka-1", 9092);
        Node broker2 = new Node(2, "kafka-2", 9092);
        TopicDescription logs = new TopicDescription("logs.raw", false, List.of(
                new TopicPartitionInfo(0, broker1, List.of(broker1, broker2), List.of(broker1)),
                new TopicPartitionInfo(1, null, List.of(broker1, broker2), List.of())));
        TopicDescription dlt = new TopicDescription("logs.raw.DLT", false, List.of(
                new TopicPartitionInfo(0, broker1, List.of(broker1, broker2), List.of(broker1, broker2))));
        TopicPartition logs0 = new TopicPartition("logs.raw", 0);
        TopicPartition logs1 = new TopicPartition("logs.raw", 1);
        TopicPartition dlt0 = new TopicPartition("logs.raw.DLT", 0);

        KafkaPlatformMonitor.KafkaSnapshot snapshot = KafkaPlatformMonitor.calculateSnapshot(
                2, List.of(logs, dlt), "logs.raw", "logs.raw.DLT",
                Map.of(logs0, 10L, logs1, 20L, dlt0, 5L),
                Map.of(logs0, 30L, logs1, 50L, dlt0, 8L),
                Map.of(logs0, new OffsetAndMetadata(25L)));

        assertThat(snapshot.brokerCount()).isEqualTo(2);
        assertThat(snapshot.underReplicatedPartitions()).isEqualTo(2);
        assertThat(snapshot.offlinePartitions()).isEqualTo(1);
        assertThat(snapshot.consumerLag()).isEqualTo(35);
        assertThat(snapshot.dltPendingRecords()).isEqualTo(3);
    }

    @Test
    void clampsExpiredCommittedOffsetToBeginningOffset() {
        Node broker = new Node(1, "kafka", 9092);
        TopicDescription logs = new TopicDescription("logs.raw", false, List.of(
                new TopicPartitionInfo(0, broker, List.of(broker), List.of(broker))));
        TopicDescription dlt = new TopicDescription("logs.raw.DLT", false, List.of());
        TopicPartition partition = new TopicPartition("logs.raw", 0);

        KafkaPlatformMonitor.KafkaSnapshot snapshot = KafkaPlatformMonitor.calculateSnapshot(
                1, List.of(logs, dlt), "logs.raw", "logs.raw.DLT",
                Map.of(partition, 100L), Map.of(partition, 120L),
                Map.of(partition, new OffsetAndMetadata(50L)));

        assertThat(snapshot.consumerLag()).isEqualTo(20);
    }

    @Test
    void marksKafkaUnavailableWhenAdminCollectionFails() {
        Admin admin = mock(Admin.class);
        when(admin.describeCluster()).thenThrow(new IllegalStateException("broker unavailable"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KafkaPlatformMonitor monitor = new KafkaPlatformMonitor(admin, new KafkaMonitoringProperties(),
                new LogIngestProperties(), new PlatformMetrics(registry));

        monitor.refresh();

        assertThat(registry.get("log_platform_kafka_available").gauge().value()).isZero();
        assertThat(registry.get("log_platform_kafka_monitor_failures_total").counter().count()).isEqualTo(1);
    }
}
