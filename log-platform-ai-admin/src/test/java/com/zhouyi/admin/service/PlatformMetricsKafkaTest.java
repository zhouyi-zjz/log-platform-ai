package com.zhouyi.admin.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformMetricsKafkaTest {

    @Test
    void exposesSuccessfulAndFailedKafkaMonitoringState() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PlatformMetrics metrics = new PlatformMetrics(registry);

        metrics.updateKafkaSnapshot(3, 3, 1, 0, 42, 2);
        metrics.markKafkaPublishFailure();

        assertThat(gauge(registry, "log_platform_kafka_available")).isEqualTo(1);
        assertThat(gauge(registry, "log_platform_kafka_broker_count")).isEqualTo(3);
        assertThat(gauge(registry, "log_platform_consumer_lag")).isEqualTo(42);
        assertThat(gauge(registry, "log_platform_dlt_pending_records")).isEqualTo(2);
        assertThat(gauge(registry, "log_platform_kafka_monitor_last_success_unixtime")).isPositive();
        assertThat(registry.counter("log_platform_kafka_publish_failures_total").count()).isEqualTo(1);

        metrics.markKafkaMonitorFailure();

        assertThat(gauge(registry, "log_platform_kafka_available")).isZero();
        assertThat(gauge(registry, "log_platform_consumer_lag")).isEqualTo(-1);
        assertThat(registry.counter("log_platform_kafka_monitor_failures_total").count()).isEqualTo(1);
    }

    private static double gauge(SimpleMeterRegistry registry, String name) {
        return registry.get(name).gauge().value();
    }
}
