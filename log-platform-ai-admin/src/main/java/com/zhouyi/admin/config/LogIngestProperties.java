package com.zhouyi.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 日志采集链路的集中配置，避免在 Controller、Kafka 和 ES 代码中散落魔法值。 */
@ConfigurationProperties(prefix = "log-platform.ingest")
public class LogIngestProperties {

    /** Kafka 日志 Topic；建议生产环境使用 Data Stream 对应的独立 Topic。 */
    private String kafkaTopic = "logs.raw";
    /** 是否由 Spring 启动时向 Kafka 申请创建 Topic；生产建议由 IaC 管理，因此默认关闭。 */
    private boolean topicAutoCreate = false;
    /** 自动创建 Topic 时使用的分区数。 */
    private int topicPartitions = 6;
    /** 自动创建 Topic 时使用的副本因子。 */
    private short topicReplicationFactor = 2;
    /** 接收接口的最大请求体字节数，防止异常堆栈撑爆内存。 */
    private int maxPayloadBytes = 1024 * 1024;
    /** 全局兼容采集 Token；项目 Token 启用后建议清空，生产环境必须由 Secret 注入。 */
    private String apiToken = "";
    /** HTTP 入口等待 Kafka broker ack 的最长时间。 */
    private int kafkaAckTimeoutMs = 5000;
    /** ES 地址，多个地址用逗号分隔。 */
    private String elasticsearchEndpoints = "http://localhost:9200";
    /** ES Data Stream 或写入别名。 */
    private String elasticsearchIndex = "logs-platform-default";
    /** ES Basic Auth 用户名，建议生产改用 Secret。 */
    private String elasticsearchUsername = "";
    /** ES Basic Auth 密码，禁止写入日志。 */
    private String elasticsearchPassword = "";
    /** 单次 Kafka poll 和 ES Bulk 的最大动作数。 */
    private int bulkMaxActions = 1000;
    /** Kafka listener 两次 poll 之间的空闲间隔毫秒数。 */
    private int bulkFlushIntervalMs = 1000;

    public String getKafkaTopic() { return kafkaTopic; }
    public void setKafkaTopic(String kafkaTopic) { this.kafkaTopic = kafkaTopic; }
    public boolean isTopicAutoCreate() { return topicAutoCreate; }
    public void setTopicAutoCreate(boolean topicAutoCreate) { this.topicAutoCreate = topicAutoCreate; }
    public int getTopicPartitions() { return topicPartitions; }
    public void setTopicPartitions(int topicPartitions) { this.topicPartitions = topicPartitions; }
    public short getTopicReplicationFactor() { return topicReplicationFactor; }
    public void setTopicReplicationFactor(short topicReplicationFactor) { this.topicReplicationFactor = topicReplicationFactor; }
    public int getMaxPayloadBytes() { return maxPayloadBytes; }
    public void setMaxPayloadBytes(int maxPayloadBytes) { this.maxPayloadBytes = maxPayloadBytes; }
    public String getApiToken() { return apiToken; }
    public void setApiToken(String apiToken) { this.apiToken = apiToken; }
    public int getKafkaAckTimeoutMs() { return kafkaAckTimeoutMs; }
    public void setKafkaAckTimeoutMs(int kafkaAckTimeoutMs) { this.kafkaAckTimeoutMs = kafkaAckTimeoutMs; }
    public String getElasticsearchEndpoints() { return elasticsearchEndpoints; }
    public void setElasticsearchEndpoints(String endpoints) { this.elasticsearchEndpoints = endpoints; }
    public String getElasticsearchIndex() { return elasticsearchIndex; }
    public void setElasticsearchIndex(String index) { this.elasticsearchIndex = index; }
    public String getElasticsearchUsername() { return elasticsearchUsername; }
    public void setElasticsearchUsername(String username) { this.elasticsearchUsername = username; }
    public String getElasticsearchPassword() { return elasticsearchPassword; }
    public void setElasticsearchPassword(String password) { this.elasticsearchPassword = password; }
    public int getBulkMaxActions() { return bulkMaxActions; }
    public void setBulkMaxActions(int bulkMaxActions) { this.bulkMaxActions = bulkMaxActions; }
    public int getBulkFlushIntervalMs() { return bulkFlushIntervalMs; }
    public void setBulkFlushIntervalMs(int bulkFlushIntervalMs) { this.bulkFlushIntervalMs = bulkFlushIntervalMs; }
}
