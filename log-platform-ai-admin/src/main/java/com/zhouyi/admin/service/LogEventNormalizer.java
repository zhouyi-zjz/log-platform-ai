package com.zhouyi.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.zhouyi.common.domain.log.LogEvent;
import com.zhouyi.common.domain.telemetry.TelemetryContext;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <p>采集器之间的字段形态经常不一致：有的发送 {@code http.method}，
 * 有的发送 {@code http: {"method": "GET"}}。这里统一处理这些差异，
 * 让 Elasticsearch mapping 和查询 API 只面对一种结构。</p>
 *
 * @author zjz
 * @version 1.0
 * @description: 将 Fluent Bit、应用日志或 HTTP 入口的 JSON 归一化为平台固定字段。
 * @date 2026/9/12
 */
@Service
public class LogEventNormalizer {

    private final ObjectMapper objectMapper;

    public LogEventNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LogEvent normalize(String json) {
        return normalize(json, null);
    }

    public LogEvent normalize(String json, String fallbackEventId) {
        return normalize(json, fallbackEventId, null);
    }

    /** 解析 JSON 并使用消息 Header 中的可信上下文补齐缺失关联字段。 */
    public LogEvent normalize(String json, String fallbackEventId, TelemetryContext context) {
        try {
            return normalize(objectMapper.readTree(json), fallbackEventId, context);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("日志 JSON 格式无效", exception);
        }
    }

    public LogEvent normalize(JsonNode node) {
        return normalize(node, null);
    }

    public LogEvent normalize(JsonNode node, String fallbackEventId) {
        return normalize(node, fallbackEventId, null);
    }

    /** 归一化日志并在 metadata-only 约束下补齐 Trace/Request 关联信息。 */
    public LogEvent normalize(JsonNode node, String fallbackEventId, TelemetryContext context) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("日志消息必须是 JSON 对象");
        }
        ObjectNode normalized = ((ObjectNode) node).deepCopy();
        enrichContext(normalized, context);
        String projectId = text(normalized, "project.id");
        if (projectId.isBlank()) {
            throw new IllegalArgumentException("project.id 不能为空");
        }

        Instant timestamp = instant(normalized, "@timestamp", Instant.now());
        // event.ingested 必须由平台写入，不能信任业务侧传入的时间。
        Instant ingested = Instant.now();
        return new LogEvent(
                timestamp,
                ingested,
                eventId(normalized, fallbackEventId),
                projectId,
                text(normalized, "deployment.environment"),
                text(normalized, "service.name"),
                text(normalized, "service.instance.id"),
                text(normalized, "trace.id"),
                text(normalized, "span.id"),
                text(normalized, "request.id"),
                text(normalized, "log.level"),
                text(normalized, "log.logger"),
                text(normalized, "code.namespace"),
                text(normalized, "code.function"),
                text(normalized, "message"),
                text(normalized, "error.type"),
                text(normalized, "error.stack_trace"),
                collectMap(normalized, "http"),
                text(normalized, "user.id_hash"),
                collectMap(normalized, "attributes"));
    }

    /** 仅补齐缺失字段，绝不允许消息 Header 覆盖业务事件已有的可信关联值。 */
    private static void enrichContext(ObjectNode node, TelemetryContext context) {
        if (context == null) {
            return;
        }
        if (text(node, "trace.id").isBlank() && context.traceId() != null) {
            node.put("trace.id", context.traceId());
        }
        if (text(node, "span.id").isBlank() && context.spanId() != null) {
            node.put("span.id", context.spanId());
        }
        if (text(node, "request.id").isBlank() && context.requestId() != null) {
            node.put("request.id", context.requestId());
        }
    }

    private static String eventId(JsonNode node, String fallbackEventId) {
        String supplied = text(node, "event.id").trim();
        String resolved = supplied.isBlank() ? fallbackEventId : supplied;
        if (resolved == null || resolved.isBlank()) {
            resolved = UUID.randomUUID().toString();
        }
        if (resolved.getBytes(StandardCharsets.UTF_8).length > 512) {
            throw new IllegalArgumentException("event.id 不能超过 512 字节");
        }
        return resolved;
    }

    private Map<String, Object> collectMap(JsonNode node, String prefix) {
        Map<String, Object> values = new LinkedHashMap<>();
        JsonNode nested = node.get(prefix);
        if (nested != null && nested.isObject()) {
            nested.fields().forEachRemaining(entry ->
                    values.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class)));
        }
        node.fields().forEachRemaining(entry -> {
            String name = entry.getKey();
            String marker = prefix + ".";
            if (name.startsWith(marker)) {
                values.put(name.substring(marker.length()),
                        objectMapper.convertValue(entry.getValue(), Object.class));
            }
        });
        return values.isEmpty() ? null : values;
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static Instant instant(JsonNode node, String name, Instant fallback) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return fallback;
        }
        try {
            if (value.isNumber()) {
                return Instant.ofEpochMilli(value.asLong());
            }
            return Instant.parse(value.asText());
        } catch (DateTimeParseException | ArithmeticException exception) {
            throw new IllegalArgumentException(name + " 必须是 ISO-8601 时间或毫秒时间戳", exception);
        }
    }
}
