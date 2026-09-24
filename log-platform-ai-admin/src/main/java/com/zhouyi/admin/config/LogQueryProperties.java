package com.zhouyi.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日志查询的安全和性能边界。
 *
 * <p>这些值不是前端可修改的业务参数，而是平台保护 ES 的硬限制。
 * 生产环境应根据压测结果调整，并通过配置中心统一管理。</p>
 */
@ConfigurationProperties(prefix = "log-platform.query")
public class LogQueryProperties {

    /** 普通用户允许查询的最大页大小。 */
    private int maxPageSize = 500;
    /** ES 单次查询超时时间，格式如 2s、1500ms。 */
    private String timeout = "3s";
    /** HTTP 连接超时。 */
    private int connectTimeoutMs = 1000;
    /** HTTP 读取超时，必须略大于 ES query timeout。 */
    private int readTimeoutMs = 5000;
    /** 管理员最多可回溯的小时数。 */
    private int adminMaxHours = 168;
    /** 单个用户/IP 每分钟最多查询次数。 */
    private int rateLimitPerMinute = 120;
    /** 全局并行查询数，防止 Web 请求把 ES 线程池打满。 */
    private int maxConcurrent = 20;
    /** 上下文接口单侧最多读取的日志条数。 */
    private int maxContextSize = 100;

    public int getMaxPageSize() { return maxPageSize; }
    public void setMaxPageSize(int maxPageSize) { this.maxPageSize = maxPageSize; }
    public String getTimeout() { return timeout; }
    public void setTimeout(String timeout) { this.timeout = timeout; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    public int getAdminMaxHours() { return adminMaxHours; }
    public void setAdminMaxHours(int adminMaxHours) { this.adminMaxHours = adminMaxHours; }
    public int getRateLimitPerMinute() { return rateLimitPerMinute; }
    public void setRateLimitPerMinute(int rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }
    public int getMaxConcurrent() { return maxConcurrent; }
    public void setMaxConcurrent(int maxConcurrent) { this.maxConcurrent = maxConcurrent; }
    public int getMaxContextSize() { return maxContextSize; }
    public void setMaxContextSize(int maxContextSize) { this.maxContextSize = maxContextSize; }
}
