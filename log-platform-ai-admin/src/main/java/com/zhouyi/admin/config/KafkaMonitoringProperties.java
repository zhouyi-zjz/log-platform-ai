package com.zhouyi.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Kafka 主链路自监控配置。 */
@ConfigurationProperties(prefix = "log-platform.kafka-monitoring")
public class KafkaMonitoringProperties {

    /** 是否启用 Kafka Admin API 周期采集。 */
    private boolean enabled;
    /** 单次 Admin API 调用超时。 */
    private int timeoutMs = 5000;
    /** 告警所依据的预期 broker 数。 */
    private int expectedBrokers = 1;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    public int getExpectedBrokers() { return expectedBrokers; }
    public void setExpectedBrokers(int expectedBrokers) { this.expectedBrokers = expectedBrokers; }
}
