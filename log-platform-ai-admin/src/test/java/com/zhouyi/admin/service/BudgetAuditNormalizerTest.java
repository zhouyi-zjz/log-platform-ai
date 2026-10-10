package com.zhouyi.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FinOps 预算审计事件归一化测试。 */
class BudgetAuditNormalizerTest {

    private final BudgetAuditNormalizer normalizer = new BudgetAuditNormalizer(new ObjectMapper());

    /** 应保留审计关联字段和 FinOps 给出的金额，不在日志平台重新计算。 */
    @Test
    void normalizesReadOnlyAuditMetadata() {
        var event = normalizer.normalize("""
                {"schema.version":"1","event.id":"evt-1","event.type":"BUDGET_DECISION",
                 "event.occurred_at":"2026-10-09T00:00:00Z","tenant.id":"t1","project.id":"p1",
                 "decision":"DENY","reason.code":"BUDGET_EXHAUSTED","amount.micros":1200,"currency":"USD"}
                """);
        assertThat(event.eventId()).isEqualTo("evt-1");
        assertThat(event.projectId()).isEqualTo("p1");
        assertThat(event.amountMicros()).isEqualTo(1200L);
    }

    /** 缺失必要字段时必须拒绝消息并交给 Kafka DLT。 */
    @Test
    void rejectsInvalidEvent() {
        assertThatThrownBy(() -> normalizer.normalize("{\"schema.version\":\"1\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("event.id");
    }

    /** 事件契约中的裁决结果是必填枚举，不能把不完整消息投影成合法审计记录。 */
    @Test
    void rejectsMissingDecision() {
        assertThatThrownBy(() -> normalizer.normalize("""
                {"schema.version":"1","event.id":"evt-1","event.type":"BUDGET_DECISION",
                 "event.occurred_at":"2026-10-09T00:00:00Z","tenant.id":"t1","project.id":"p1"}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decision");
    }

    /** 日志平台必须在边界拒绝正文、非法枚举和负金额，而不是依赖下游解释。 */
    @Test
    void rejectsSensitiveOrOutOfContractFields() {
        assertThatThrownBy(() -> normalizer.normalize("""
                {"schema.version":"1","event.id":"evt-1","event.type":"BUDGET_DECISION",
                 "event.occurred_at":"2026-10-09T00:00:00Z","tenant.id":"t1","project.id":"p1",
                 "decision":"ALLOW","prompt":"do not persist"}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("敏感正文字段");

        assertThatThrownBy(() -> normalizer.normalize("""
                {"schema.version":"1","event.id":"evt-1","event.type":"UNKNOWN",
                 "event.occurred_at":"2026-10-09T00:00:00Z","tenant.id":"t1","project.id":"p1",
                 "decision":"ALLOW","amount.micros":-1}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("event.type");
    }

    /** canonical envelope 必须通过 ACL 映射为日志平台只读审计投影。 */
    @Test
    void normalizesCanonicalFinopsEnvelope() {
        var event = normalizer.normalize("""
                {"schemaVersion":"1.0","eventId":"decision-1","eventType":"BUDGET_DECISION",
                 "occurredAt":"2026-10-09T00:00:00Z","tenantId":1001,"projectId":"project-a",
                 "environment":"prod","serviceName":"agent-platform","traceId":"0123456789abcdef0123456789abcdef",
                 "requestId":"req-1","payload":{"decision":"REJECT","reason_code":"BUDGET_EXHAUSTED","mode":"ENFORCE","policy_version":3}}
                """);
        assertThat(event.eventId()).isEqualTo("decision-1");
        assertThat(event.eventType()).isEqualTo("BUDGET_DECISION");
        assertThat(event.decision()).isEqualTo("DENY");
        assertThat(event.tenantId()).isEqualTo("1001");
        assertThat(event.projectId()).isEqualTo("project-a");
    }

    /** UNKNOWN 事件必须保留未知语义，不能被日志投影误报为 SETTLED。 */
    @Test
    void preservesUnknownSettlementState() {
        var event = normalizer.normalize("""
                {"schemaVersion":"1.0","eventId":"reservation-1:settlement","eventType":"UNKNOWN",
                 "occurredAt":"2026-10-09T00:00:00Z","tenantId":1001,"projectId":"project-a",
                 "environment":"prod","serviceName":"agent-platform","reservationId":"res-1",
                 "payload":{"state":"UNKNOWN","usage_known":false}}
                """);
        assertThat(event.decision()).isEqualTo("UNKNOWN");
    }

    /** canonical payload 的敏感正文必须在 Anti-Corruption Layer 处拒绝。 */
    @Test
    void rejectsSensitiveCanonicalPayload() {
        assertThatThrownBy(() -> normalizer.normalize("""
                {"schemaVersion":"1.0","eventId":"decision-1","eventType":"BUDGET_DECISION",
                 "occurredAt":"2026-10-09T00:00:00Z","tenantId":1001,"projectId":"project-a",
                 "environment":"prod","serviceName":"agent-platform",
                 "payload":{"decision":"ALLOW","prompt":"must not persist"}}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("敏感字段");
    }
}
