package com.zhouyi.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouyi.common.domain.telemetry.BudgetAuditEvent;
import com.zhouyi.common.domain.telemetry.TelemetrySchema;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/** 将 FinOps 预算审计事件校验并归一化为日志平台的只读投影。 */
@Service
public class BudgetAuditNormalizer {

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
