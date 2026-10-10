# 分阶段开发任务与详细设计

## 1. 目标和边界

本轮以 `current-architecture-and-blueprint.md` 为唯一架构基线，采用“先闭环正确性，再扩展可观测与部署基线，最后完善产品”的顺序。当前代码继续使用模块化单体：API、Kafka Consumer、ES Writer、导出 Worker 在同一代码库，K3s 通过资源和开关提供部署基线。任何未经过 Kafka/ES/K3s/Redis 真实环境验证的数字，都只作为压测输入，不作为 SLA 承诺。

## 2. 阶段任务

### Phase 0：正确性与安全闭环（本轮实现）

| 任务 | 设计 | 完成标准 |
|---|---|---|
| 项目独立 Token | `log_project.ingest_token_hash` 保存 SHA-256；管理员轮换接口只返回一次明文 Token；兼容全局 Token 由 Secret 注入 | 项目 Token 只能访问同项目，数据库无明文 |
| 请求体保护 | `LogIngestRequestFilter` 在 Jackson 解析前检查 Content-Length，并限制 chunked 输入流 | 超过 1 MiB 返回 413，业务对象不会被构造 |
| Kafka 批次配置 | Consumer 的 `max.poll.records` 读取 `bulk-max-actions`，listener 的 poll 间隔读取 `bulk-flush-interval-ms` | 配置修改能改变批次行为 |
| 查询脱敏 | `LogFieldRedactor` 对对象/数组递归处理，覆盖 token、secret、password、authorization、cookie 等字段 | 列表与详情不返回凭据明文 |
| ES 超时 | 检查响应 `timed_out`，映射为 504；不把部分结果误报成成功 | 客户端收到可重试的超时语义 |
| 查询审计 | 记录用户名、操作、结果、项目、耗时和截断说明；不写入日志正文/关键字 | 关键查询可追溯且不扩大敏感面 |
| 详情和上下文 | 前端详情改走受 ACL 和时间窗约束的服务端接口，增加上下文加载 | 列表快照不能绕过服务端权限 |

### Phase 1：高可用、可观测与容量认证（代码基线已补齐，现场门禁待验证）

1. **已实现基线**：Actuator/Micrometer 指标、HTTP RED/Trace 完整率/消息传播指标、Redis Session/分布式限流开关、PDB/HPA/NetworkPolicy、Redis StatefulSet 和部署变量。
2. **已实现代码边界**：W3C Trace Context、Kafka Header 传播、SDK 无关的异步 Metadata 传播端口、Tempo 日志/Trace 联查、Prometheus 告警规则和 Collector 尾采样/发送队列。
3. **已实现部署边界**：API 与 Consumer 独立 Deployment，Consumer 不暴露业务 Service；默认仍是单节点开发拓扑。
4. **仍待实现/验证**：Kafka 至少 3 Broker、RF=3、minISR=2；ES 至少 3 节点、TLS、认证、副本和 Snapshot；MySQL 使用受管 HA。
5. **仍待验证**：Prometheus 抓取、Grafana/Alertmanager 告警、Kafka lag 和 JVM/磁盘面板；当前已提供采集点和告警规则。
6. **仍待验证**：按 60/150/300/450 EPS 阶梯、600 EPS 突发、24 小时稳定性和故障重启矩阵完成容量报告。

通过标准：450 EPS 持续、600 EPS 突发、20 并发混合查询；无已确认消息丢失；端到端可见延迟 P95 < 5 秒。

### Phase 2：日志产品能力（P0 相关闭环已实现）

1. **已实现**：前端接入聚合 API，提供服务/环境/级别 Top 分布摘要；聚合失败不影响主查询。
2. **已实现**：异步 CSV 导出，复用 RBAC、时间窗、字段脱敏和查询服务；任务状态为 `QUEUED/RUNNING/SUCCEEDED/FAILED/EXPIRED`，有界队列、10,000 行/50 MiB 上限和限时下载。
3. **已实现**：Tempo 查询、受 ACL 约束的 Trace/日志联查和前端瀑布图；服务拓扑和 Span 详情仍属于后续增强。
4. **已实现**：Prometheus 告警规则与 Runbook；通知渠道、静默、升级策略和告警历史由目标 Alertmanager/告警平台完成联调。
5. **已实现**：日志与 FinOps DLT 的固定白名单状态查询、单 partition/offset 安全重放、审计和指标；不支持任意目标 Topic 或批量重放。
6. **已实现**：`finops.budget-audit.v1` 冻结 Schema、metadata-only 校验、独立 ES 投影、幂等写入和 ACL 查询。
7. **仍待实现**：字段选择、项目切换、保存查询、分享链接、对象存储导出结果和采集 SDK/接入向导。

### Phase 3：规模化与 AI

1. 固定看板使用预聚合/Transform；按容量引入 hot/warm 或对象存储归档。
2. 引入项目级 EPS、存储、查询 CPU、导出量和 Trace 采样配额。
3. AI 作为异步任务域，只能生成受控查询参数，不能直接提交任意 ES DSL。
4. AI 请求实施脱敏、审计、缓存、超时、降级和离线效果评测，不阻塞基础检索链路。

## 3. 关键详细设计

### 3.1 采集 API

`POST /monitor/api/logs/ingest` 的处理顺序固定为：

1. Filter 检查请求方法、路径和字节上限；超限直接返回 413。
2. Controller 校验 JSON 对象大小、项目 ID 和采集 Token。
3. `LogIngestService` 补齐 `event.id`，按 `project.id|service.name|deployment.environment` 计算 Kafka key。
4. 等待 Kafka broker ack，成功返回 202、事件 ID、Topic、分区和 offset；失败返回 503。
5. 202 只代表 Kafka 已确认，不代表 Elasticsearch 已可查询。

项目 Token 轮换：

`POST /monitor/api/admin/projects/{projectId}/ingest-token/rotate` 仅允许管理员。服务生成 32 字节随机值，存储 `SHA-256(token)`，响应只返回一次 `{ "token": "..." }`。采集器应通过 Secret 管理 token，并在轮换后重启/热更新。已有环境执行 `sql/migrations/V1__project_token_and_query_audit.sql`。

### 3.2 消费与写入

`LogKafkaConsumer` 使用批量监听。Normalizer 强制写入 `event.ingested`，缺少 `event.id` 时优先采用采集端值，否则使用 `topic/partition/offset` 后备值。Writer 以 `SHA-256(project.id + NUL + event.id)` 作为 `_id`，Bulk 使用 `create`：

- 2xx：写入成功；
- 409：重复消费，视为幂等成功；
- 其他状态：抛出异常，由 Kafka ErrorHandler 退避重试，最终进入 DLT。

`bulk-max-actions` 同时限制 Kafka `max.poll.records`，避免配置名义与实际批次不一致；真实 ES 往返耗时仍需压测校准。

### 3.3 查询服务

服务端只接受 `LogSearchRequest` 定义的字段，固定执行：

- 权限窗口：普通用户当天，管理员最多 168 小时；
- 项目范围：管理员全量，普通用户从 MySQL 查询启用授权；无授权 fail closed；
- 排序：`@timestamp desc, event.id desc`，search_after 必须恰好两个值；
- 聚合：最多 3 层，每层最多 100 bucket；
- 保护：ES timeout、连接/读取超时、并发 Semaphore、用户/IP 分钟窗口。

ES 响应若 `timed_out=true`，转换为 504。`LogFieldRedactor` 在返回前深拷贝 `_source` 并递归遮盖凭据字段，因此上下文和详情同样不会绕过脱敏。

### 3.4 审计模型

`log_query_audit` 字段：

| 字段 | 说明 |
|---|---|
| `username` | 服务端会话中的用户名 |
| `operation` | `search` / `detail` / `aggregation` |
| `outcome` | 当前为 `SUCCESS`，异常路径由 Controller 日志和后续 Phase 1 统一补齐 |
| `project_id` | 请求明确指定的项目，可为空 |
| `duration_ms` | 服务端耗时 |
| `detail` | 最多 512 字符的非正文备注 |
| `created_at` | 审计时间，用于保留和归档 |

审计写入采用尽力而为，审计表短暂不可用时不阻断日志查询；Phase 1 增加写入失败指标和告警。

### 3.5 异步导出设计（Phase 2 已实现基线）

导出接口直接复用 `LogSearchRequest`，不接受 Elasticsearch DSL：

1. `POST /monitor/api/logs/exports`：登录后解析项目 ACL，校验 `searchAfter` 为空、页大小合法，创建 `QUEUED` 任务并返回 `202`。
2. Worker 使用有界线程池执行分页查询，每页最多使用后端允许的页大小；每页调用 `LogQueryService.search`，因此继续强制时间窗、项目 ACL、字段白名单和递归脱敏。
3. CSV 只输出固定字段（文档 ID、时间、项目、服务、Trace、级别、消息和异常），对象/数组使用 JSON 字符串；逗号、换行、引号按 RFC 4180 转义，疑似 Excel 公式前缀加单引号。
4. 单任务最多 10,000 行、默认 50 MiB；生成成功后保留 30 分钟。状态查询和下载都再次校验任务所有者，管理员可查看全部任务。
5. `GET /monitor/api/logs/exports/{jobId}` 返回状态；`GET /monitor/api/logs/exports/{jobId}/download` 仅允许 `SUCCEEDED`，过期返回 `410`，未完成返回 `409`。

当前结果保存在应用内存，适合单实例/小规模基线；Redis、对象存储、跨副本任务队列和导出审计属于后续生产化工作。真实 ES 大数据量、断点恢复和多副本一致性尚未验证。

## 4. API 契约

| API | 权限 | 成功 | 失败 |
|---|---|---|---|
| `POST /monitor/api/logs/ingest` | 项目 Token | `202` + Kafka receipt | `400`/`401`/`413`/`503` |
| `POST /monitor/api/logs/search` | 登录 + 项目 ACL | `200` + items/cursor | `400`/`403`/`429`/`504` |
| `GET /monitor/api/logs/{id}` | 登录 + 项目 ACL + 时间窗 | `200` + 脱敏详情 | `403`/`404`/`504` |
| `POST /monitor/api/logs/{id}/context` | 登录 + 项目 ACL | `200` + 上下文 items | `400`/`403`/`504` |
| `POST /monitor/api/logs/aggregations` | 登录 + 项目 ACL | `200` + 白名单聚合 | `400`/`403`/`504` |
| `POST /monitor/api/logs/exports` | 登录 + 项目 ACL | `202` + 导出任务状态 | `400`/`403`/`429` |
| `GET /monitor/api/logs/exports/{jobId}` | 任务所有者或管理员 | `200` + 任务状态 | `400`/`403`/`404` |
| `GET /monitor/api/logs/exports/{jobId}/download` | 任务所有者或管理员 | `200` + `text/csv` | `403`/`404`/`409`/`410` |
| `GET /monitor/api/traces/{traceId}` | 登录 + 项目 ACL | `200` + 日志与 Tempo Trace | `400`/`403`/`504` |
| `POST /monitor/api/finops/budget-audit/search` | 登录 + 项目 ACL | `200` + 只读审计事件/游标 | `400`/`403`/`429`/`504` |
| `GET /monitor/api/admin/dlt/topics` | `ROLE_ADMIN` | `200` + 固定 DLT 分区状态 | `403`/`503` |
| `POST /monitor/api/admin/dlt/replay` | `ROLE_ADMIN` | `200` + 单条重放结果 | `400`/`403`/`503` |
| `POST /monitor/api/admin/projects/{id}/ingest-token/rotate` | `ROLE_ADMIN` | `200` + 一次性 Token | `400`/`404` |

## 5. 发布与回滚

1. 先执行 SQL migration，再发布后端；新列允许为空，旧项目仍可使用全局 Token。
2. 生成项目 Token 后把 `LOG_INGEST_TOKEN` 迁移到项目 Secret，验证 202 和 ES 可查，再清空全局兼容 Token。
3. 前端与后端同版本发布；详情接口失败时前端保留列表快照并显示错误，不阻塞查询列表。
4. 回滚后端时保留新增列和审计表；旧版本忽略新增列，不会破坏用户、项目和日志主链路。

## 6. 验证清单

- Maven：单元测试、MockMvc、H2 Schema 初始化；默认 Session 不连接 Redis。
- Vue：`npm run build:prod`，覆盖日志分析摘要和 Trace 瀑布图构建。
- Shell/YAML：K3s、P0 端到端、生产门禁和观测栈脚本语法与 manifest 解析。
- 未完成项：真实 Kafka/ES/K3s/OTel/Tempo 联调、压测、告警通知、备份恢复和故障演练。
