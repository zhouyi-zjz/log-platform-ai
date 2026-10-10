package com.zhouyi.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日志平台与独立 FinOps 服务之间的只读边界配置。
 *
 * <p>日志平台不计算 Token、成本或预算，只允许未来通过独立事件通道关联外部事实。
 * 默认 DISABLED，避免在没有完成租户映射和契约验收前误发业务事件。</p>
 */
@ConfigurationProperties(prefix = "log-platform.finops")
public class FinopsIntegrationProperties {

    /** FinOps 集成运行模式。 */
    private Mode mode = Mode.DISABLED;
    /** Usage Event 的独立 Kafka 通道名称。 */
    private String usageTopic = "finops.usage.v1";
    /** Budget Decision 的独立 Kafka 通道名称。 */
    private String budgetAuditTopic = "finops.budget-audit.v1";
    /** 外部 FinOps 请求的有界超时时间。 */
    private int timeoutMs = 500;
    /** 外部 FinOps 不可用时是否允许日志平台继续工作。 */
    private boolean failOpen = true;
    /** 是否禁止把 Prompt、Completion 等正文写入关联事件。 */
    private boolean metadataOnly = true;
    /** 是否启动预算审计事件的只读消费者。 */
    private boolean budgetAuditConsumerEnabled;
    /** 预算审计消费者组。 */
    private String budgetAuditConsumerGroup = "log-platform-budget-audit";
    /** 预算审计事件在 Elasticsearch 中的独立索引。 */
    private String budgetAuditIndex = "finops-budget-audit-v1";
    /** 单条预算审计事件的最大字节数。 */
    private int budgetAuditMaxPayloadBytes = 64 * 1024;

    /** FinOps 集成模式。 */
    public enum Mode {
        /** 不建立外部 FinOps 连接。 */
        DISABLED,
        /** 只验证映射和契约，不改变业务行为。 */
        SHADOW,
        /** 允许向独立 FinOps 服务发送已冻结的事件。 */
        ACTIVE
    }

    /** 获取运行模式。 */
    public Mode getMode() { return mode; }

    /** 设置运行模式。 */
    public void setMode(Mode mode) { this.mode = mode == null ? Mode.DISABLED : mode; }

    /** 获取 Usage Event Topic。 */
    public String getUsageTopic() { return usageTopic; }

    /** 设置 Usage Event Topic。 */
    public void setUsageTopic(String usageTopic) { this.usageTopic = usageTopic; }

    /** 获取预算审计 Topic。 */
    public String getBudgetAuditTopic() { return budgetAuditTopic; }

    /** 设置预算审计 Topic。 */
    public void setBudgetAuditTopic(String budgetAuditTopic) { this.budgetAuditTopic = budgetAuditTopic; }

    /** 获取外部调用超时。 */
    public int getTimeoutMs() { return timeoutMs; }

    /** 设置外部调用超时并限制在可控范围。 */
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = Math.max(1, Math.min(timeoutMs, 10_000)); }

    /** 判断外部 FinOps 故障是否允许日志平台继续工作。 */
    public boolean isFailOpen() { return failOpen; }

    /** 设置 FinOps 故障策略。 */
    public void setFailOpen(boolean failOpen) { this.failOpen = failOpen; }

    /** 判断是否启用 metadata-only 约束。 */
    public boolean isMetadataOnly() { return metadataOnly; }

    /** 设置 metadata-only 约束。生产环境不允许关闭。 */
    public void setMetadataOnly(boolean metadataOnly) { this.metadataOnly = metadataOnly; }

    /** 判断是否启动预算审计只读消费者。 */
    public boolean isBudgetAuditConsumerEnabled() { return budgetAuditConsumerEnabled; }

    /** 设置预算审计只读消费者开关。 */
    public void setBudgetAuditConsumerEnabled(boolean enabled) { this.budgetAuditConsumerEnabled = enabled; }

    /** 获取预算审计消费者组。 */
    public String getBudgetAuditConsumerGroup() { return budgetAuditConsumerGroup; }

    /** 设置预算审计消费者组。 */
    public void setBudgetAuditConsumerGroup(String group) {
        this.budgetAuditConsumerGroup = group == null || group.isBlank() ? "log-platform-budget-audit" : group.trim();
    }

    /** 获取预算审计独立索引。 */
    public String getBudgetAuditIndex() { return budgetAuditIndex; }

    /** 设置预算审计独立索引。 */
    public void setBudgetAuditIndex(String index) {
        this.budgetAuditIndex = index == null || index.isBlank() ? "finops-budget-audit-v1" : index.trim();
    }

    /** 获取预算审计事件最大字节数。 */
    public int getBudgetAuditMaxPayloadBytes() { return budgetAuditMaxPayloadBytes; }

    /** 设置预算审计事件最大字节数并限制在可控范围。 */
    public void setBudgetAuditMaxPayloadBytes(int bytes) {
        this.budgetAuditMaxPayloadBytes = Math.max(1024, Math.min(bytes, 1024 * 1024));
    }

    /** 判断是否允许发送到外部服务。 */
    public boolean isActive() { return mode == Mode.ACTIVE; }
}
