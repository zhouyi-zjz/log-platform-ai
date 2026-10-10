package com.zhouyi.common.domain.telemetry;

/**
 * 平台事件通道。
 *
 * <p>不同通道拥有不同的数据所有者和保留策略。日志平台只负责 LOGS，
 * AI_USAGE 与 BUDGET_AUDIT 由独立 FinOps 服务消费，避免把日志索引当作财务账本。</p>
 */
public enum TelemetryEventChannel {
    /** 结构化日志事件。 */
    LOGS,
    /** 模型物理调用的 Usage Event。 */
    AI_USAGE,
    /** 预算裁决和审计事件。 */
    BUDGET_AUDIT
}
