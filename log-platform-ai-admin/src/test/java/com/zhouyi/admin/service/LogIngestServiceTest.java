package com.zhouyi.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouyi.admin.config.LogIngestProperties;
import com.zhouyi.admin.config.TelemetryContextHolder;
import com.zhouyi.common.domain.telemetry.TelemetryContext;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogIngestServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsReceiptOnlyAfterKafkaAcknowledgesAndAddsEventId() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        @SuppressWarnings("unchecked")
        SendResult<String, String> sendResult = mock(SendResult.class);
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("logs.raw", 2), 42L, 0, System.currentTimeMillis(), 0, 0);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
        LogIngestProperties properties = new LogIngestProperties();
        LogIngestService service = new LogIngestService(kafkaTemplate, objectMapper, properties);

        var receipt = service.publish(objectMapper.readTree("""
                {"project.id":"billing","service.name":"api","message":"ok"}
                """));

        assertThat(receipt.topic()).isEqualTo("logs.raw");
        assertThat(receipt.partition()).isEqualTo(2);
        assertThat(receipt.offset()).isEqualTo(42L);
        assertThat(receipt.eventId()).isNotBlank();
        ArgumentCaptor<ProducerRecord<String, String>> record = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(record.capture());
        assertThat(objectMapper.readTree(record.getValue().value()).path("event.id").asText())
                .isEqualTo(receipt.eventId());
    }

    @Test
    void rejectsRequestWhenKafkaDoesNotAcknowledgeBeforeTimeout() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> pending = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(pending);
        LogIngestProperties properties = new LogIngestProperties();
        properties.setKafkaAckTimeoutMs(1);
        LogIngestService service = new LogIngestService(kafkaTemplate, objectMapper, properties);

        assertThatThrownBy(() -> service.publish(objectMapper.readTree("""
                {"project.id":"billing","message":"timeout"}
                """)))
                .isInstanceOf(LogIngestService.LogPublishException.class)
                .hasMessageContaining("Kafka 未在规定时间内确认日志");
    }

    /** HTTP 入口上下文缺失时，发布事件应补齐 trace/span/request 关联字段。 */
    @Test
    void enrichesPublishedEventWithTrustedTelemetryContext() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        @SuppressWarnings("unchecked")
        SendResult<String, String> sendResult = mock(SendResult.class);
        when(sendResult.getRecordMetadata()).thenReturn(
                new RecordMetadata(new TopicPartition("logs.raw", 0), 1L, 0, 0L, 0, 0));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
        LogIngestService service = new LogIngestService(kafkaTemplate, objectMapper, new LogIngestProperties(),
                null, null, new KafkaTracePropagation(new SimpleMeterRegistry()));
        TelemetryContextHolder.set(new TelemetryContext(null, "billing", "api", "prod",
                "req-1", "4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", null));

        service.publish(objectMapper.readTree("{\"project.id\":\"billing\",\"message\":\"ok\"}"));

        ArgumentCaptor<ProducerRecord<String, String>> record = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(record.capture());
        assertThat(objectMapper.readTree(record.getValue().value()).path("trace.id").asText())
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(objectMapper.readTree(record.getValue().value()).path("request.id").asText()).isEqualTo("req-1");
        assertThat(record.getValue().headers().lastHeader(KafkaTracePropagation.TRACEPARENT_HEADER)).isNotNull();
        TelemetryContextHolder.clear();
    }
}
