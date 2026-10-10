# 可观测平台运行手册

## 接入拒绝率（ingest）

1. 检查 `/actuator/prometheus` 的 `log_platform_ingest_rejected_total` 与 HTTP 入口返回码。
2. 检查 Kafka broker、Topic 分区、Producer ack 和 DLT 是否有新增消息。
3. 如果 Kafka 不可用，保持业务应用继续写本地/Fluent Bit 磁盘缓冲；不要把日志平台重试改成无限同步等待。

## Elasticsearch（elasticsearch）

1. 查看 `log_platform_bulk_failures_total`、Consumer lag 和 `logs.raw.DLT`。
2. 确认 ES 集群健康、Data Stream 写入权限和 ILM 状态。
3. 先恢复 ES，再按 DLT/offset 重放；重复事件依靠 `project.id + event.id` 幂等。

## 查询（query）

1. 查看 `log_platform_query_latency_seconds` 的 P95/P99 和 ES `timed_out` 响应。
2. 收窄查询时间范围、项目范围或页大小；不要开放任意 DSL。
3. 检查 Redis 分布式限流和查询并发信号，必要时临时降低导出并发。

## Trace（trace）

1. 查看管理员接口 `/monitor/api/admin/observability/trace-completeness` 的聚合快照。
2. 如果缺少 `trace.id` 或 `span.id`，检查采集器是否保留 `traceparent`、应用 OTel Agent 和 Kafka Header。
3. 指标只记录缺失字段计数，不记录 Trace ID，避免 Prometheus 高基数。

## Tempo（tempo）

Tempo 联查是增强能力。`TEMPO_QUERY_ENABLED=false` 或 Tempo 不可用时，
`/monitor/api/traces/{traceId}` 仍返回受 ACL 约束的日志，`tempoTrace` 为空；不要因为 Tempo
故障阻断日志接入、查询或 FinOps 事件。

## FinOps 审计（finops-audit）

预算审计只读消费者只接收 `finops.budget-audit.v1`，写入 `finops-budget-audit-v1` 索引。
它不计算成本、不修改 Ledger，也不执行预算裁决。先检查
`log_platform_finops_audit_write_failures_total` 和该 Topic 的 DLT，再由 FinOps 团队确认源事件
是否符合 `docs/ingest/budget-audit-event-v1.schema.json`。审计事件默认关闭，启用前必须完成
租户/项目映射、Topic ACL 和 metadata-only 验收。

## DLT（dlt）

管理员可查看 `/monitor/api/admin/dlt/topics` 的分区积压，并使用
`POST /monitor/api/admin/dlt/replay` 按固定 Topic、分区和 offset 重放单条消息。重放不提交 DLT
消费位点，不支持任意目标 Topic 或批量操作；每次操作都会写入 `log_query_audit`。重放前先确认
根因已修复，重放后核对幂等事件 ID、ES 文档和 Kafka offset。
