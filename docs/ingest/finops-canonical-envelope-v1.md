# FinOps Canonical Envelope v1

统一跨服务 Topic 只使用：

- `ai.usage.v1`：Usage Event，供独立 FinOps 入账服务消费；
- `ai.budget.audit.v1`：Budget Decision、Reservation、Settlement、UNKNOWN，供 FinOps 和统一日志平台只读消费；
- 对应死信为 Topic 名称追加 `.DLT`。

消息外层字段固定为 `schemaVersion`、`eventId`、`eventType`、`occurredAt`、`tenantId`、`projectId`、`environment`、`serviceName`、`traceId`、`requestId`、`runId`、`modelCallId`、`attemptId`、`reservationId`、`payload`。`payload` 只允许预算、用量和关联元数据，不允许 Prompt、Completion、消息数组、工具参数或凭据。

日志平台收到 envelope 后必须经过 Anti-Corruption Layer 转换为自己的 dotted-field 查询投影；日志平台 ES 文档不是预算账本，不参与预算裁决或额度扣减。
