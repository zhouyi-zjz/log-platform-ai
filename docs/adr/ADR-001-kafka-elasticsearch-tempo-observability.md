# ADR-001：日志平台采用 Kafka + Elasticsearch，并以 Tempo 承载链路追踪

- 状态：Accepted（容量基线中的峰值 EPS 仍待业务方确认）
- 日期：2026-09-11
- 决策人：日志平台项目组
- 关联约束：5 个接入项目；日志最多保留 7 天；普通用户只能查询当天；管理员最多查询 7 天；日志日量为百万级。

## 背景

平台需要为多个项目提供统一日志检索和 traceId 全链路排障能力。日志必须能够按项目、环境、服务、级别、traceId、类名、方法名和关键字检索，并且在业务高峰期间不阻塞业务服务。日志检索与链路追踪的查询模型不同：日志是高吞吐事件流，Trace 是带父子关系和耗时的 Span 图。

本 ADR 将日志链路和 Trace 链路分开决策，避免把 Elasticsearch、Tempo、Kafka 的职责混在一起。

## 决策

### 1. 日志写入链路

```text
业务服务 stdout(JSON)
  -> Fluent Bit/Vector
  -> Kafka
  -> 日志规范化/路由消费者
  -> Elasticsearch Data Stream
```

- Kafka 作为异步缓冲、削峰和可重放层，生产环境至少 3 个 broker。
- Kafka 消息 key 使用 `project.id + service.name + deployment.environment` 的稳定哈希，兼顾分区负载和局部顺序。
- 消费失败进入死信 Topic；消费者提交 offset 必须在 ES 写入成功后进行。
- 采集器启用本地磁盘缓冲；Kafka 不可用时不能让业务进程同步等待。
- 浏览器和业务服务均不得直接访问 Kafka 或 Elasticsearch，统一通过日志平台 API。

### 2. 日志存储与生命周期

- Elasticsearch 固定为 8.x 系列，部署时锁定具体补丁版本，使用 Data Stream、组件模板和 ILM。
- 日志最多保留 7 天；第 8 天由 ILM 删除，删除策略必须以集群时区和 `@timestamp` 为准。
- 推荐共享 Data Stream，通过 `project.id`、`deployment.environment` 做逻辑隔离，避免 5 个项目每天创建大量小索引。
- 默认生命周期：Hot 0～2 天，Warm 3～7 天（若集群规模不足，可先全部 Hot），第 8 天删除。
- `project.id`、环境、服务、trace/span id、级别、类名、方法名使用 `keyword`；消息和堆栈使用受控全文字段；禁止无限动态字段。

### 3. Trace 链路

```text
业务服务(OpenTelemetry Java Agent)
  -> OTLP
  -> OpenTelemetry Collector
  -> Tempo
```

- Tempo 是 Trace 的后端，保存 Span、父子关系、服务图和耗时；它不是 Elasticsearch 的存储引擎。
- 日志中的 `trace.id`、`span.id` 与 OTel Span 使用同一 W3C Trace Context。
- 平台查询某个 trace 时，同时查询 Elasticsearch 日志和 Tempo Span，再在 API 层聚合成时间线。
- Kafka/ES 仅承载日志；OTLP/Tempo 仅承载 Trace，禁止双写同一份 Span。

### 4. 查询窗口与权限

- 普通用户：后端强制把查询窗口裁剪为“当前自然日 00:00:00 至当前时间”，不得相信前端传入的更早 `from`。
- 管理员：最多查询最近 7×24 小时；超过 7 天的请求直接拒绝。
- 所有查询必须带明确时间范围；单页最多 500 条，使用 `search_after`，不使用深分页。
- 导出走异步任务，复用相同的权限和时间窗口，并记录审计事件。

## 不采用的方案

### 不采用“仅 Elasticsearch，不使用 Kafka”

在日志突发、ES 重平衡或短暂不可用时，采集器需要可靠缓冲和重放能力；仅依赖采集器本地缓存无法满足多节点、长时间积压和统一重放需求。

### 不采用“仅靠日志 traceId 拼链路”

日志可以形成时间线，但不能可靠表达父子 Span、服务耗时、重试和错误节点；完整链路必须采集 OTel Span。

### 不采用“Tempo 使用 Elasticsearch 作为存储”

Tempo 的推荐对象存储后端与 Elasticsearch 日志索引模型不同。强行共用 ES 会增加索引、查询和升级耦合，且无法替代日志检索需求。

## 容量与运行假设

- 当前 5 个项目，每项目按 100 万条/天的保守上限建模，总计 500 万条/天。
- 由于需求中的峰值 EPS 尚未填入，当前设计峰值暂按平均流量 5 倍计算，即 300 EPS；实际 EPS 到位后必须替换该参数并重新压测。
- 事件平均大小、压缩率、副本数和查询并发见 [容量模型](../capacity/log-platform-capacity-model.md)。

## 后果

### 正向后果

- Kafka 吸收突发，业务服务与 ES 写入解耦，可重放和补写。
- ES 适合结构化过滤、全文查询和 7 天短周期生命周期管理。
- Tempo 保留真正的链路语义，日志和 Trace 仍通过 trace/span id 关联。
- 普通用户的当天窗口和管理员 7 天窗口在服务端可强制执行。

### 代价与风险

- 需要维护 Kafka Topic、消费者、死信和 offset 监控。
- ES、Kafka、Tempo 需要独立容量、升级和故障演练。
- Kafka 至少 3 broker、ES 至少 3 data 节点是生产建议，MVP 可用单节点开发拓扑。
- 峰值 EPS、平均事件大小和查询并发未最终确认，容量结论不能直接作为生产采购量。

## 验证计划

1. 使用 500 万条/天等效数据，压测 300 EPS 设计峰值和 2 倍突发。
2. 验证 Kafka 单 broker 故障、消费者重启、ES 短暂不可用时不丢日志。
3. 验证普通用户无法通过搜索、trace、上下文或导出越过当天窗口。
4. 验证管理员最多读取 7 天且 ILM 在第 8 天删除数据。
5. 验证日志 `trace.id` 与 Tempo Trace 100% 关联，且重试/异步边界不生成错误根 Trace。

## 待确认事项

- 需求中的“峰值 EPS 为”缺少具体数值；在正式采购和生产分片数确定前，必须补齐该数字。
- Elasticsearch 仅要求 8.x 兼容基线；正式部署前需在发布清单中补齐具体 minor/patch、镜像摘要和校验值，并针对该版本完成兼容性与容量压测。
