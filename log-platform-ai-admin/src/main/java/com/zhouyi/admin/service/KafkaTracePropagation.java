package com.zhouyi.admin.service;

import com.zhouyi.common.domain.telemetry.TelemetryContext;
import com.zhouyi.common.domain.telemetry.W3cTraceContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Kafka 消息边界的 W3C Trace Context 适配器。
 *
 * <p>传播失败只会返回空上下文，不抛出异常，不阻塞日志生产或消费；消息内容本身仍按
 * 原有幂等和重试语义处理。</p>
 */
@Component
public class KafkaTracePropagation {

    /** Kafka Header 中保存标准 W3C traceparent 的键名。 */
    public static final String TRACEPARENT_HEADER = "traceparent";
    /** Kafka Header 中保存平台请求 ID 的键名。 */
    public static final String REQUEST_ID_HEADER = "x-request-id";

    private final Counter injected;
    private final Counter extracted;
    private final Counter missing;

    /** 注册固定低基数的消息传播指标。 */
    public KafkaTracePropagation(MeterRegistry registry) {
        injected = Counter.builder("log_platform_messaging_trace_injected_total")
                .description("写入 Kafka 消息的 Trace Context 数").register(registry);
        extracted = Counter.builder("log_platform_messaging_trace_extracted_total")
                .description("从 Kafka 消息提取的 Trace Context 数").register(registry);
        missing = Counter.builder("log_platform_messaging_trace_missing_total")
                .description("缺少或非法 Trace Context 的 Kafka 消息数").register(registry);
    }

    /** 将当前请求上下文注入 ProducerRecord。 */
    public void inject(ProducerRecord<String, String> record, TelemetryContext context) {
        if (record == null || context == null || context.traceId() == null || context.spanId() == null) {
            missing.increment();
            return;
        }
        W3cTraceContext trace = new W3cTraceContext(context.traceId(), context.spanId(), "01");
        if (trace.isValid()) {
            record.headers().remove(TRACEPARENT_HEADER)
                    .add(TRACEPARENT_HEADER, trace.traceparent().getBytes(StandardCharsets.UTF_8));
            injected.increment();
        } else {
            missing.increment();
        }
        if (context.requestId() != null && !context.requestId().isBlank()) {
            record.headers().remove(REQUEST_ID_HEADER)
                    .add(REQUEST_ID_HEADER, context.requestId().getBytes(StandardCharsets.UTF_8));
        }
    }

    /** 从 ConsumerRecord 提取上下文，非法 Header 按缺失处理。 */
    public TelemetryContext extract(ConsumerRecord<?, ?> record) {
        if (record == null) {
            missing.increment();
            return null;
        }
        return extract(record.headers());
    }

    /** 从通用 Kafka Headers 提取上下文，便于单元测试和其他消息适配器复用。 */
    public TelemetryContext extract(Headers headers) {
        if (headers == null) {
            missing.increment();
            return null;
        }
        String traceparent = headerValue(headers.lastHeader(TRACEPARENT_HEADER));
        W3cTraceContext trace = W3cTraceContext.parse(traceparent);
        if (trace == null) {
            missing.increment();
            return null;
        }
        extracted.increment();
        return TelemetryContext.ofRequest(headerValue(headers.lastHeader(REQUEST_ID_HEADER)),
                trace.traceId(), trace.spanId());
    }

    /** 安全读取 Header 的 UTF-8 值，避免异常消息影响消费线程。 */
    private static String headerValue(Header header) {
        return header == null || header.value() == null
                ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
