package com.zhouyi.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 日志采集链路的集中配置，避免在 Controller、Kafka 和 ES 代码中散落魔法值。 */
@ConfigurationProperties(prefix = "log-platform.ingest")
public class LogIngestProperties {

    /** Kafka 日志 Topic；建议生产环境使用 Data Stream 对应的独立 Topic。 */
    private String kafkaTopic = "logs.raw";
    /** 是否由 Spring 启动时向 Kafka 申请创建 Topic；生产建议由 IaC 管理，因此默认关闭。 */
    private boolean topicAutoCreate = false;
    private int topicPartitions = 6;
    private short topicReplicationFactor = 2;
    /** 接收接口的最大请求体字节数，防止异常堆栈撑爆内存。 */
    private int maxPayloadBytes = 1024 * 1024;
    /** 可选的采集 Token。为空时仅适合本地开发，生产必须配置。 */
    private String apiToken = "";
    /** ES 地址，多个地址用逗号分隔。 */
    private String elasticsearchEndpoints = "http://localhost:9200";
    /** ES Data Stream 或写入别名。 */
    private String elasticsearchIndex = "logs-platform-default";
    private String elasticsearchUsername = "";
    private String elasticsearchPassword = "";
    private int bulkMaxActions = 1000;
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
