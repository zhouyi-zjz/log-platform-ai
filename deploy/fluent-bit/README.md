# Fluent Bit 接入说明

应用推荐输出单行 JSON，并至少包含 `project.id`、`service.name`、`deployment.environment`、`message`。
OpenTelemetry Java Agent 产生的 trace/span 信息，应通过 MDC/日志框架写入 `trace.id`、`span.id`，再由 Fluent Bit 原样转发。

直接 Kafka 输出时，采集器不经过后台 HTTP 接口；需要配置 `KAFKA_BROKERS`、TLS/SASL 和文件系统 buffer。
如果环境只能使用 HTTP，可将输出改成 `http` 插件，地址为：

```text
POST http://log-platform:8103/monitor/api/logs/ingest
Content-Type: application/json
X-Log-Ingest-Token: <项目采集 Token>
```

HTTP 接口只有收到 Kafka broker ack 才返回 `202`，但这仍不表示 Elasticsearch 已经写入。
