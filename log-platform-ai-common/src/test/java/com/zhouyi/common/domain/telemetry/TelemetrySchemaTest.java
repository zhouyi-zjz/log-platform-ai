package com.zhouyi.common.domain.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 预算审计事件 v1 的边界校验测试。 */
class TelemetrySchemaTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** 合法元数据事件应通过校验。 */
    @Test
    void acceptsMetadataOnlyBudgetAuditEvent() throws Exception {
        var node = mapper.readTree("""
                {"schema.version":"1","event.id":"evt-1","event.type":"BUDGET_DECISION",
                 "event.occurred_at":"2026-10-09T00:00:00Z","tenant.id":"t1","project.id":"p1",
                 "decision":"ALLOW","trace.id":"4bf92f3577b34da6a3ce929d0e0e4736"}
                """);
        assertThat(TelemetrySchema.validateBudgetAudit(node)).isNull();
    }

    /** 正文和凭据字段必须被拒绝，防止预算审计通道泄露业务数据。 */
    @Test
    void rejectsSensitiveBodyFields() throws Exception {
        var node = mapper.readTree("""
                {"schema.version":"1","event.id":"evt-1","event.type":"BUDGET_DECISION",
                 "event.occurred_at":"2026-10-09T00:00:00Z","tenant.id":"t1","project.id":"p1",
                 "decision":"DENY","prompt":"secret"}
                """);
        assertThat(TelemetrySchema.validateBudgetAudit(node)).contains("prompt");
    }
}
