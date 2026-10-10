# 日志采集框架

当前代码把采集链路拆成四层：

```text
OpenTelemetry Agent
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
`X-Log-Ingest-Token`。接口收到 Kafka broker ack 后才返回 `202`，响应包含 `eventId`、分区和 offset；
这不代表 ES 已经写入。

生产环境建议为每个项目签发独立 Token：管理员调用
`POST /monitor/api/admin/projects/{projectId}/ingest-token/rotate`，数据库只保存 Token 的
SHA-256 摘要，接口响应中的明文 Token 只出现一次。项目配置了独立 Token 后，该项目不会再接受全局
`LOG_INGEST_TOKEN`；轮换前请先更新采集器 Secret。全局 Token 仅用于兼容尚未迁移的项目。

每条日志应提供稳定的 `event.id`。HTTP 入口会为缺失 ID 的事件生成 UUID；Fluent Bit 直写 Kafka
且未提供 ID 时，消费端使用 Kafka topic、partition、offset 生成稳定后备 ID。ES 使用
`project.id + event.id` 的 SHA-256 作为文档 ID 执行 Bulk create，重复消费产生的 409 被视为
幂等成功，同时避免不同项目使用相同事件 ID 时互相冲突。

## 配置启动

```bash
DB_URL='jdbc:mysql://mysql:3306/log-platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
DB_USERNAME='logplatform' \
DB_PASSWORD='replace-with-database-secret' \
KAFKA_BOOTSTRAP_SERVERS=kafka-1:9092,kafka-2:9092 \
KAFKA_SECURITY_PROTOCOL=PLAINTEXT \
LOG_CONSUMER_ENABLED=true \
LOG_INGEST_TOKEN='replace-with-secret' \
ELASTICSEARCH_ENDPOINTS='https://es-1:9200,https://es-2:9200' \
ELASTICSEARCH_INDEX=logs-platform-default \
java -jar log-platform-ai-admin/target/log-platform-ai-admin-1.0.0.jar
```

应用不提供数据库连接默认值；`DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 必须由本地环境或 Secret 显式注入。

生产环境应由 IaC 创建 Kafka Topic、ES Data Stream、组件模板和 ILM；只有本地联调才建议设置
`LOG_TOPIC_AUTO_CREATE=true`。ES 模板和字段示例分别见 `deploy/elasticsearch/`、
`docs/ingest/log-event-example.json`。

## Trace 关联

Trace 采集统一使用 OpenTelemetry Agent：应用通过 OTLP 将 Span 发送到 Collector，再由
`otlp/tempo` exporter 转发到 Tempo。应用日志必须把当前 Trace/Span 上下文写入
`trace.id`、`span.id`，Fluent Bit 不修改这两个字段。配置见 `deploy/observability/`。
