package com.zhouyi.common.domain.telemetry;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * FinOps 预算审计事件的只读投影契约。
 *
 * <p>该模型只承载预算裁决的关联和审计元数据，不承载 Prompt、Completion、工具参数或凭据。
 * 成本账本和预算状态仍以独立 FinOps 服务为权威来源。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BudgetAuditEvent(
        /** 固定契约版本。 */
        @JsonProperty("schema.version") String schemaVersion,
        /** FinOps 侧全局幂等事件 ID。 */
        @JsonProperty("event.id") String eventId,
        /** 审计事件类型，例如 BUDGET_DECISION 或 RESERVATION_EXPIRED。 */
        @JsonProperty("event.type") String eventType,
        /** 事件发生时间。 */
        @JsonProperty("event.occurred_at") Instant occurredAt,
        /** 租户标识。 */
        @JsonProperty("tenant.id") String tenantId,
        /** 项目标识。 */
        @JsonProperty("project.id") String projectId,
        /** 部署环境。 */
        @JsonProperty("deployment.environment") String environment,
        /** 预算裁决结果。 */
        String decision,
        /** 稳定的拒绝或状态原因码。 */
        @JsonProperty("reason.code") String reasonCode,
        /** 关联 Trace ID。 */
        @JsonProperty("trace.id") String traceId,
        /** 关联请求 ID。 */
        @JsonProperty("request.id") String requestId,
        /** Agent 运行 ID，只作为关联字段。 */
        @JsonProperty("run.id") String runId,
        /** 预算预占 ID，只作为审计关联字段。 */
        @JsonProperty("reservation.id") String reservationId,
        /** 预算策略 ID。 */
        @JsonProperty("budget.policy.id") String budgetPolicyId,
        /** FinOps 侧给出的金额，日志平台不计算或修改。 */
        @JsonProperty("amount.micros") Long amountMicros,
        /** 金额币种。 */
        String currency) {
}
