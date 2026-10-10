package com.zhouyi.common.domain.telemetry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 统一上下文和事件通道的契约测试。 */
class TelemetryContextTest {

    /** 空请求 ID 必须由平台生成，不能把空字符串传播到下游。 */
    @Test
    void generatesRequestIdForEmptyInput() {
        TelemetryContext context = TelemetryContext.ofRequest(" ", "trace", "span");

        assertThat(context.effectiveRequestId()).isNotBlank();
        assertThat(context.traceId()).isEqualTo("trace");
    }

    /** 三类事件通道必须保持独立，避免日志索引承担 FinOps 权威数据。 */
    @Test
    void exposesIndependentEventChannels() {
        assertThat(TelemetryEventChannel.values())
                .containsExactly(TelemetryEventChannel.LOGS,
                        TelemetryEventChannel.AI_USAGE,
                        TelemetryEventChannel.BUDGET_AUDIT);
    }
}
