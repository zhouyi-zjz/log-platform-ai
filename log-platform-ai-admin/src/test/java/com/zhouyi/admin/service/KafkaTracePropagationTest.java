package com.zhouyi.admin.service;

import com.zhouyi.common.domain.telemetry.TelemetryContext;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/** Kafka Trace Context 注入和提取测试。 */
class KafkaTracePropagationTest {

    /** Producer Header 应能被 Consumer 侧恢复为同一 Trace/Request 上下文。 */
    @Test
    void injectsAndExtractsTraceContext() {
        KafkaTracePropagation propagation = new KafkaTracePropagation(new SimpleMeterRegistry());
        ProducerRecord<String, String> producer = new ProducerRecord<>("logs.raw", "key", "{}");
        propagation.inject(producer, new TelemetryContext(null, "billing", "api", "prod",
                "request-1", "4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", null));
        ConsumerRecord<String, String> consumer = new ConsumerRecord<>("logs.raw", 0, 1L,
                "key", "{}");
        producer.headers().forEach(header -> consumer.headers().add(header.key(), header.value()));

        var context = propagation.extract(consumer);

        assertThat(context).isNotNull();
        assertThat(context.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(context.requestId()).isEqualTo("request-1");
    }
}
