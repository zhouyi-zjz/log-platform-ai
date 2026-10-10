package com.zhouyi.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhouyi.admin.config.LogIngestProperties;
import com.zhouyi.admin.config.TelemetryContextHolder;
import com.zhouyi.common.domain.telemetry.TelemetryContext;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 *
 * @author zjz
 * @version 1.0
 * @description: 将采集入口收到的原始 JSON 投递到 Kafka，不在 Web 请求线程中访问 ES
 * @date 2026/9/12
 */
@Service
public class LogIngestService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final LogIngestProperties properties;
    private final ProjectTokenService projectTokenService;
    private final PlatformMetrics metrics;
    private final KafkaTracePropagation tracePropagation;

    /** 保留无数据库单元测试构造器；生产应用使用带项目 Token 服务的构造器。 */
    public LogIngestService(KafkaTemplate<String, String> kafkaTemplate,
                            ObjectMapper objectMapper,
                            LogIngestProperties properties) {
        this(kafkaTemplate, objectMapper, properties, null, null, null);
    }

    /** 创建支持全局兼容 Token 和项目独立 Token 的采集服务。 */
    public LogIngestService(KafkaTemplate<String, String> kafkaTemplate,
                            ObjectMapper objectMapper,
                            LogIngestProperties properties,
                            ProjectTokenService projectTokenService) {
        this(kafkaTemplate, objectMapper, properties, projectTokenService, null, null);
    }

    /** 创建带指标埋点的生产采集服务。 */
    @Autowired
    public LogIngestService(KafkaTemplate<String, String> kafkaTemplate,
                            ObjectMapper objectMapper,
                            LogIngestProperties properties,
                            ProjectTokenService projectTokenService,
                            PlatformMetrics metrics,
                            KafkaTracePropagation tracePropagation) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.projectTokenService = projectTokenService;
        this.metrics = metrics;
        this.tracePropagation = tracePropagation;
    }

    /** 校验并发布单条日志，只有 Kafka broker ack 后才返回接收回执。 */
    public PublishReceipt publish(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("日志消息必须是 JSON 对象");
        }
        JsonNode projectId = payload.get("project.id");
        if (projectId == null || projectId.asText().isBlank()) {
            throw new IllegalArgumentException("project.id 不能为空");
        }
        ObjectNode event = ((ObjectNode) payload).deepCopy();
        enrichTraceContext(event);
        String eventId = text(event, "event.id").trim();
        if (eventId.isBlank()) {
            eventId = UUID.randomUUID().toString();
            event.put("event.id", eventId);
        }
        if (eventId.getBytes(StandardCharsets.UTF_8).length > 512) {
            throw new IllegalArgumentException("event.id 不能超过 512 字节");
        }
        event.put("event.id", eventId);
        long sample = metrics == null ? 0 : metrics.startIngestAck();
        try {
            String body = objectMapper.writeValueAsString(event);
            // 使用项目、服务和环境作为稳定 key，既保证同一业务日志顺序，也避免单一项目独占分区。
            String key = String.join("|",
                    text(event, "project.id"),
                    text(event, "service.name"),
                    text(event, "deployment.environment"));
            ProducerRecord<String, String> record = new ProducerRecord<>(properties.getKafkaTopic(), key, body);
            if (tracePropagation != null) {
                tracePropagation.inject(record, TelemetryContextHolder.current());
            }
            SendResult<String, String> result = kafkaTemplate.send(record)
                    .get(Math.max(1, properties.getKafkaAckTimeoutMs()), TimeUnit.MILLISECONDS);
            var metadata = result.getRecordMetadata();
            if (metrics != null) {
                metrics.markIngestAccepted();
            }
            return new PublishReceipt(eventId, metadata.topic(), metadata.partition(), metadata.offset());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            markPublishFailure();
            throw new LogPublishException("等待 Kafka 确认时线程被中断", exception);
        } catch (ExecutionException | TimeoutException exception) {
            markPublishFailure();
            throw new LogPublishException("Kafka 未在规定时间内确认日志", exception);
        } catch (Exception exception) {
            markPublishFailure();
            throw new LogPublishException("日志序列化或 Kafka 发布失败", exception);
        } finally {
            if (metrics != null) {
                metrics.recordIngestAck(sample);
            }
        }
    }

    /** 记录发布失败但不改变原有异常语义。 */
    private void markPublishFailure() {
        if (metrics != null) {
            metrics.markIngestRejected();
        }
    }

    /** Token 校验放在服务层，便于未来迁移到网关或为不同项目配置独立 Token。 */
    public boolean isValidToken(String suppliedToken) {
        String expected = properties.getApiToken();
        return expected != null && !expected.isBlank() && suppliedToken != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8));
    }

    /** 优先校验项目独立 Token；未配置项目 Token 时才允许使用受控的全局兼容 Token。 */
    public boolean isValidToken(String suppliedToken, JsonNode payload) {
        String projectId = text(payload, "project.id").trim();
        if (projectTokenService != null && projectTokenService.hasConfiguredToken(projectId)) {
            return projectTokenService.isValid(projectId, suppliedToken);
        }
        return isValidToken(suppliedToken);
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    /** 将入口可信上下文补到日志事件；客户端已提供的合法关联字段保持不变。 */
    private static void enrichTraceContext(ObjectNode event) {
        TelemetryContext context = TelemetryContextHolder.current();
        if (context == null) {
            return;
        }
        if (text(event, "trace.id").isBlank() && context.traceId() != null) {
            event.put("trace.id", context.traceId());
        }
        if (text(event, "span.id").isBlank() && context.spanId() != null) {
            event.put("span.id", context.spanId());
        }
        if (text(event, "request.id").isBlank() && context.requestId() != null) {
            event.put("request.id", context.requestId());
        }
    }

    public record PublishReceipt(String eventId, String topic, int partition, long offset) {
    }

    public static final class LogPublishException extends RuntimeException {
        public LogPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
