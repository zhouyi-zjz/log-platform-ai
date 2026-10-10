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
    /** 是否使用 Redis 共享用户/IP 限流；关闭时使用单实例内存实现。 */
    private boolean distributedRateLimit = false;
    /** 全局并行查询数，防止 Web 请求把 ES 线程池打满。 */
    private int maxConcurrent = 20;
    /** 上下文接口单侧最多读取的日志条数。 */
    private int maxContextSize = 100;
    /** 单个异步导出任务允许生成的最大日志行数。 */
    private int exportMaxRows = 10000;
    /** 单个异步导出任务允许占用的最大内存字节数。 */
    private long exportMaxBytes = 50L * 1024 * 1024;
    /** 导出结果保留时间，超过后不再允许下载。 */
    private int exportRetentionMinutes = 30;
    /** 导出任务清理周期，单位毫秒。 */
    private long exportCleanupIntervalMs = 60000;
    /** 导出专用线程池最大并发数。 */
    private int exportWorkerThreads = 2;
    /** 导出任务等待队列容量。 */
    private int exportQueueCapacity = 20;

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
    public boolean isDistributedRateLimit() { return distributedRateLimit; }
    public void setDistributedRateLimit(boolean distributedRateLimit) { this.distributedRateLimit = distributedRateLimit; }
    public int getMaxConcurrent() { return maxConcurrent; }
    public void setMaxConcurrent(int maxConcurrent) { this.maxConcurrent = maxConcurrent; }
    public int getMaxContextSize() { return maxContextSize; }
    public void setMaxContextSize(int maxContextSize) { this.maxContextSize = maxContextSize; }
    /** 获取单任务最大导出行数。 */
    public int getExportMaxRows() { return exportMaxRows; }
    /** 设置单任务最大导出行数。 */
    public void setExportMaxRows(int exportMaxRows) { this.exportMaxRows = exportMaxRows; }
    /** 获取单任务最大导出字节数。 */
    public long getExportMaxBytes() { return exportMaxBytes; }
    /** 设置单任务最大导出字节数。 */
    public void setExportMaxBytes(long exportMaxBytes) { this.exportMaxBytes = exportMaxBytes; }
    /** 获取导出结果保留分钟数。 */
    public int getExportRetentionMinutes() { return exportRetentionMinutes; }
    /** 设置导出结果保留分钟数。 */
    public void setExportRetentionMinutes(int exportRetentionMinutes) { this.exportRetentionMinutes = exportRetentionMinutes; }
    /** 获取过期任务清理周期。 */
    public long getExportCleanupIntervalMs() { return exportCleanupIntervalMs; }
    /** 设置过期任务清理周期。 */
    public void setExportCleanupIntervalMs(long exportCleanupIntervalMs) { this.exportCleanupIntervalMs = exportCleanupIntervalMs; }
    /** 获取导出线程数。 */
    public int getExportWorkerThreads() { return exportWorkerThreads; }
    /** 设置导出线程数。 */
    public void setExportWorkerThreads(int exportWorkerThreads) { this.exportWorkerThreads = exportWorkerThreads; }
    /** 获取导出等待队列容量。 */
    public int getExportQueueCapacity() { return exportQueueCapacity; }
    /** 设置导出等待队列容量。 */
    public void setExportQueueCapacity(int exportQueueCapacity) { this.exportQueueCapacity = exportQueueCapacity; }
}
