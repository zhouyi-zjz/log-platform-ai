# 日志采集框架

当前代码把采集链路拆成四层：

```text
OpenTelemetry（可选）/SkyWalking Agent
              ↓ Trace 上下文 + 应用日志 JSON
Fluent Bit（文件/容器 stdout、磁盘缓冲）
              ↓ Kafka logs.raw 或 HTTP 入口
Spring Boot 采集服务（校验、Kafka 发布）
              ↓ 批量消费、字段归一化
Elasticsearch 8.x Data Stream（7 天生命周期）
```

查询接口位于 `/monitor/api/logs/search`、`/{id}`、`/{id}/context` 和 `/aggregations`。
查询时间、页大小、并发数和每分钟频率均由服务端强制限制；普通用户只能查询当天，
管理员最多查询最近 168 小时，分页必须使用响应中的 `nextSearchAfter`。

## 接入方式

优先让 Fluent Bit 使用 Kafka output，配置见 `deploy/fluent-bit/fluent-bit.conf`。
无法直接访问 Kafka 的项目可以调用 `POST /monitor/api/logs/ingest`，请求头携带
`X-Log-Ingest-Token`。接口返回 `202` 表示已经提交 Kafka，不代表 ES 已经写入。

## 配置启动

```bash
KAFKA_BOOTSTRAP_SERVERS=kafka-1:9092,kafka-2:9092 \
LOG_CONSUMER_ENABLED=true \
LOG_INGEST_TOKEN='replace-with-secret' \
ELASTICSEARCH_ENDPOINTS='https://es-1:9200,https://es-2:9200' \
ELASTICSEARCH_INDEX=logs-platform-default \
java -jar log-platform-ai-admin/target/log-platform-ai-admin-1.0.0.jar
```

生产环境应由 IaC 创建 Kafka Topic、ES Data Stream、组件模板和 ILM；只有本地联调才建议设置
`LOG_TOPIC_AUTO_CREATE=true`。ES 模板和字段示例分别见 `deploy/elasticsearch/`、
`docs/ingest/log-event-example.json`。

## Trace 关联

OpenTelemetry Agent 和 SkyWalking Agent 二选一作为应用 Trace 采集入口，避免同一请求重复生成 Span。
OpenTelemetry 通过 OTLP 将 Span 发送到 Collector，再由 OTLP exporter 转发到 SkyWalking OAP；
SkyWalking Agent 则直接发送到 OAP。
无论后端选哪一种，应用日志都应把当前 Trace/Span 上下文写入 `trace.id`、`span.id`，Fluent Bit 不修改这两个字段。
若业务同时保留 SkyWalking 和 OTel，需要明确 Trace ID 映射规则并做关联验收；不建议同一 JVM 同时加载两个 Agent。
