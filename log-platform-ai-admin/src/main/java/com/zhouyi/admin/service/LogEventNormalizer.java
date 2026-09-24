package com.zhouyi.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.zhouyi.common.domain.log.LogEvent;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

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
        try {
            return normalize(objectMapper.readTree(json));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("日志 JSON 格式无效", exception);
        }
    }

    public LogEvent normalize(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("日志消息必须是 JSON 对象");
        }
        String projectId = text(node, "project.id");
        if (projectId.isBlank()) {
            throw new IllegalArgumentException("project.id 不能为空");
        }

        Instant timestamp = instant(node, "@timestamp", Instant.now());
        // event.ingested 必须由平台写入，不能信任业务侧传入的时间。
        Instant ingested = Instant.now();
        return new LogEvent(
                timestamp,
                ingested,
                projectId,
                text(node, "deployment.environment"),
                text(node, "service.name"),
                text(node, "service.instance.id"),
                text(node, "trace.id"),
                text(node, "span.id"),
                text(node, "log.level"),
                text(node, "log.logger"),
                text(node, "code.namespace"),
                text(node, "code.function"),
                text(node, "message"),
                text(node, "error.type"),
                text(node, "error.stack_trace"),
                collectMap(node, "http"),
                text(node, "user.id_hash"),
                collectMap(node, "attributes"));
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
