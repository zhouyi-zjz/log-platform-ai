package com.zhouyi.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhouyi.common.domain.telemetry.BudgetAuditEvent;
import com.zhouyi.common.domain.telemetry.TelemetrySchema;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;

/** 将 FinOps 预算审计事件校验并归一化为日志平台的只读投影。 */
@Service
public class BudgetAuditNormalizer {

    /** canonical payload 中绝对禁止进入日志平台边界的正文和凭据字段。 */
    private static final Set<String> FORBIDDEN_CANONICAL_FIELDS = Set.of(
            "prompt", "completion", "input", "output", "messages", "tool_arguments", "credentials",
            "authorization", "api_key", "secret", "response_body");

    private final ObjectMapper objectMapper;

    /** 创建预算审计事件归一化器。 */
    public BudgetAuditNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 解析、校验并转换单条预算审计事件。 */
    public BudgetAuditEvent normalize(String payload) {
        if (payload == null || payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length == 0) {
            throw new IllegalArgumentException("预算审计事件不能为空");
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            node = canonicalToProjection(node);
            String error = TelemetrySchema.validateBudgetAudit(node);
            if (error != null) {
                throw new IllegalArgumentException(error);
            }
            return new BudgetAuditEvent(
                    text(node, "schema.version"), text(node, "event.id"), text(node, "event.type"),
                    instant(node, "event.occurred_at"), text(node, "tenant.id"), text(node, "project.id"),
                    text(node, "deployment.environment"), text(node, "decision"), text(node, "reason.code"),
                    text(node, "trace.id"), text(node, "request.id"), text(node, "run.id"),
                    text(node, "reservation.id"), text(node, "budget.policy.id"),
                    node.has("amount.micros") ? node.path("amount.micros").longValue() : null,
                    text(node, "currency"));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("预算审计事件 JSON 格式无效", exception);
        }
    }

    /**
     * 将 canonical FinOps envelope 转换为日志平台自己的 dotted-field 投影。
     *
     * <p>这是明确的 Anti-Corruption Layer：日志平台只接收投影，不把自己的查询模型反向当成
     * FinOps 权威契约。旧版 dotted schema 直接原样通过，便于灰度迁移和历史重放。</p>
     */
    private JsonNode canonicalToProjection(JsonNode envelope) {
        if (envelope == null || !envelope.has("schemaVersion") || !envelope.has("eventId")) {
            return envelope;
        }
        if (!"1.0".equals(text(envelope, "schemaVersion"))) {
            throw new IllegalArgumentException("FinOps Envelope schemaVersion 必须为 1.0");
        }
        for (String field : new String[]{"eventId", "eventType", "occurredAt", "tenantId",
                "projectId", "environment", "serviceName"}) {
            if (!envelope.hasNonNull(field)) {
                throw new IllegalArgumentException("FinOps Envelope 缺少必填字段: " + field);
            }
        }
        if (!envelope.path("payload").isObject()) {
            throw new IllegalArgumentException("FinOps Envelope payload 必须是对象");
        }
        for (String field : FORBIDDEN_CANONICAL_FIELDS) {
            if (envelope.path("payload").has(field)) {
                throw new IllegalArgumentException("canonical payload 包含禁止的敏感字段: " + field);
            }
        }
        ObjectNode projection = objectMapper.createObjectNode();
        projection.put("schema.version", "1");
        projection.put("event.id", text(envelope, "eventId"));
        // 日志投影保留既有事件类型枚举；canonical UNKNOWN 通过 decision=UNKNOWN 保留未知语义。
        projection.put("event.type", "UNKNOWN".equals(text(envelope, "eventType"))
                ? "SETTLEMENT_RECORDED" : text(envelope, "eventType"));
        projection.put("event.occurred_at", text(envelope, "occurredAt"));
        projection.put("tenant.id", Long.toString(envelope.path("tenantId").longValue()));
        projection.put("project.id", text(envelope, "projectId"));
        projection.put("deployment.environment", text(envelope, "environment"));
        copyText(projection, envelope, "trace.id", "traceId");
        copyText(projection, envelope, "request.id", "requestId");
        copyText(projection, envelope, "run.id", "runId");
        copyText(projection, envelope, "reservation.id", "reservationId");
        JsonNode eventPayload = envelope.path("payload");
        String decision = decision(envelope.path("eventType").asText(), eventPayload);
        projection.put("decision", decision);
        copyPayloadText(projection, eventPayload, "reason.code", "reason_code");
        if (!projection.has("reason.code")) {
            projection.put("reason.code", decision);
        }
        if (eventPayload.has("policy_version")) {
            projection.put("budget.policy.id", eventPayload.path("policy_version").asText());
        }
        return projection;
    }

    /** 将 canonical 事件类型和领域状态映射为日志查询层有限枚举。 */
    private static String decision(String eventType, JsonNode payload) {
        if ("BUDGET_DECISION".equals(eventType)) {
            String raw = payload.path("decision").asText("UNKNOWN");
            String mode = payload.path("mode").asText();
            boolean shadow = "SHADOW".equals(mode);
            if ("REJECT".equals(raw) || "PAUSE".equals(raw) || "REQUIRE_APPROVAL".equals(raw)) {
                return shadow ? "SHADOW_DENY" : "DENY";
            }
            return shadow ? "SHADOW_ALLOW" : "ALLOW";
        }
        return switch (payload.path("state").asText("UNKNOWN")) {
            case "RELEASED" -> "RELEASED";
            case "EXPIRED" -> "EXPIRED";
            case "SETTLED" -> "SETTLED";
            default -> "UNKNOWN";
        };
    }

    /** 复制 envelope 顶层可选字符串字段。 */
    private static void copyText(ObjectNode target, JsonNode source, String targetName, String sourceName) {
        if (source.hasNonNull(sourceName)) {
            target.put(targetName, source.path(sourceName).asText());
        }
    }

    /** 复制 payload 中的可选字符串字段。 */
    private static void copyPayloadText(ObjectNode target, JsonNode source, String targetName, String sourceName) {
        if (source.hasNonNull(sourceName)) {
            target.put(targetName, source.path(sourceName).asText());
        }
    }

    /** 读取点号字段。 */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /** 解析 ISO-8601 事件时间。 */
    private static Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalArgumentException(field + " 必须是 ISO-8601 时间", exception);
        }
    }
}
