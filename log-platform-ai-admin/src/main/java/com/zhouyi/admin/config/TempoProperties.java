package com.zhouyi.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tempo 联查的边界配置，默认关闭以保持日志平台在 Trace 后端故障时 fail-open。 */
@ConfigurationProperties(prefix = "log-platform.tempo")
public class TempoProperties {

    /** 是否启用 Tempo Trace 查询。 */
    private boolean enabled = false;
    /** Tempo HTTP API 地址。 */
    private String endpoint = "http://localhost:3200";
    /** 查询超时时间毫秒。 */
    private int timeoutMs = 3000;

    /** 判断是否启用 Tempo 联查。 */
    public boolean isEnabled() { return enabled; }

    /** 设置是否启用 Tempo 联查。 */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** 获取 Tempo HTTP API 地址。 */
    public String getEndpoint() { return endpoint; }

    /** 设置并清理 Tempo HTTP API 地址。 */
    public void setEndpoint(String endpoint) { this.endpoint = endpoint == null ? "" : endpoint.trim(); }

    /** 获取联查超时。 */
    public int getTimeoutMs() { return timeoutMs; }

    /** 设置联查超时并限制最大值。 */
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = Math.max(100, Math.min(timeoutMs, 30_000)); }
}
