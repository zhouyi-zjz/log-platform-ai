package com.zhouyi.common.domain.telemetry;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

/**
 * 统一观测事件的轻量 Schema 校验器。
 *
 * <p>日志平台只校验版本、关联字段和敏感字段边界；业务成本语义由 FinOps 服务校验。</p>
 */
public final class TelemetrySchema {

    /** 当前预算审计事件版本。 */
    public static final String BUDGET_AUDIT_V1 = "1";
    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "prompt", "completion", "input", "output", "messages", "tool.arguments", "credentials");
    private static final Set<String> ALLOWED_BUDGET_AUDIT_FIELDS = Set.of(
            "schema.version", "event.id", "event.type", "event.occurred_at", "tenant.id", "project.id",
            "deployment.environment", "decision", "reason.code", "trace.id", "request.id", "run.id",
            "reservation.id", "budget.policy.id", "amount.micros", "currency");
    private static final Set<String> EVENT_TYPES = Set.of(
            "BUDGET_DECISION", "RESERVATION_CREATED", "RESERVATION_RELEASED",
            "RESERVATION_EXPIRED", "SETTLEMENT_RECORDED");
    private static final Set<String> DECISIONS = Set.of(
            "ALLOW", "DENY", "SHADOW_ALLOW", "SHADOW_DENY", "RELEASED", "EXPIRED", "SETTLED", "UNKNOWN");
    private static final Map<String, Integer> MAX_LENGTHS = Map.ofEntries(
            Map.entry("event.id", 256),
            Map.entry("tenant.id", 64),
            Map.entry("project.id", 64),
            Map.entry("deployment.environment", 64),
            Map.entry("reason.code", 128),
            Map.entry("request.id", 128),
            Map.entry("run.id", 128),
            Map.entry("reservation.id", 128),
            Map.entry("budget.policy.id", 128));

    private TelemetrySchema() {
    }

    /** 校验预算审计事件，返回可直接用于错误响应的原因；合法时返回 null。 */
    public static String validateBudgetAudit(JsonNode node) {
        if (node == null || !node.isObject()) {
            return "事件必须是 JSON 对象";
        }
        JsonNode versionNode = node.get("schema.version");
        if (versionNode == null || !versionNode.isTextual()) {
            return "schema.version 必须为字符串 1";
        }
        String version = versionNode.textValue();
        if (!BUDGET_AUDIT_V1.equals(version)) {
            return "schema.version 必须为 1";
        }
        for (String field : FORBIDDEN_FIELDS) {
            if (node.has(field)) {
                return "事件不得包含敏感正文字段: " + field;
            }
        }
        var fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!ALLOWED_BUDGET_AUDIT_FIELDS.contains(field)) {
                return "事件包含未定义字段: " + field;
            }
        }
        for (String field : new String[]{"event.id", "event.type", "event.occurred_at", "tenant.id", "project.id"}) {
            String error = requireString(node, field);
            if (error != null) {
                return error;
            }
        }
        for (Map.Entry<String, Integer> entry : MAX_LENGTHS.entrySet()) {
            JsonNode value = node.get(entry.getKey());
            if (value != null && value.isTextual() && value.textValue().length() > entry.getValue()) {
                return entry.getKey() + " 长度不能超过 " + entry.getValue();
            }
            if (value != null && !value.isTextual()) {
                return entry.getKey() + " 必须是字符串";
            }
        }
        String eventTypeError = enumValue(node, "event.type", EVENT_TYPES);
        if (eventTypeError != null) {
            return eventTypeError;
        }
        String decisionError = enumValue(node, "decision", DECISIONS);
        if (decisionError != null) {
            return decisionError;
        }
        JsonNode occurredAt = node.get("event.occurred_at");
        try {
            Instant.parse(occurredAt.textValue());
        } catch (DateTimeParseException | NullPointerException exception) {
            return "event.occurred_at 必须是 ISO-8601 时间";
        }
        for (String field : new String[]{"deployment.environment", "reason.code", "request.id", "run.id",
                "reservation.id", "budget.policy.id", "currency"}) {
            JsonNode value = node.get(field);
            if (value != null && !value.isTextual()) {
                return field + " 必须是字符串";
            }
        }
        JsonNode traceId = node.get("trace.id");
        if (traceId != null && (!traceId.isTextual() || !traceId.textValue().matches("[0-9a-fA-F]{32}"))) {
            return "trace.id 必须是 32 位十六进制字符串";
        }
        JsonNode currency = node.get("currency");
        if (currency != null && (!currency.isTextual() || !currency.textValue().matches("[A-Z]{3}"))) {
            return "currency 必须是 3 位大写字母";
        }
        if (node.has("amount.micros")) {
            JsonNode amount = node.get("amount.micros");
            if (!amount.isIntegralNumber() || !amount.canConvertToLong()) {
                return "amount.micros 必须是整数";
            }
            if (amount.longValue() < 0) {
                return "amount.micros 不能小于 0";
            }
        }
        return null;
    }

    /** 校验必填字符串字段，避免 Jackson 将数字或布尔值静默转成文本。 */
    private static String requireString(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.textValue().isBlank()) {
            return field + " 不能为空且必须是字符串";
        }
        return null;
    }

    /** 校验枚举字段并明确返回缺失、类型或值错误。 */
    private static String enumValue(JsonNode node, String field, Set<String> allowed) {
        String requiredError = requireString(node, field);
        if (requiredError != null) {
            return requiredError;
        }
        if (!allowed.contains(node.get(field).textValue())) {
            return field + " 值不在允许范围内";
        }
        return null;
    }

    /** 读取点号字段并统一空值语义。 */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.isTextual() ? value.textValue() : value.asText();
    }
}
