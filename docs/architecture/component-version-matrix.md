# 组件版本矩阵

版本原则：日志平台锁定一套可重复构建的版本；升级必须同时通过日志写入、查询、ILM、OTLP、Trace 聚合和故障演练。Tempo 不依赖 Elasticsearch 存储，二者只在 API 层通过 `trace.id` 关联。

## 1. 当前锁定与状态

| 组件 | 版本 | 状态 | 兼容说明/验证动作 |
|---|---:|---|---|
| Java | 21 LTS | 已锁定 | 与当前 Spring Boot 工程一致 |
| Spring Boot | 3.5.3 | 已锁定 | 当前 Maven 父工程版本 |
| Spring Security | 6.5.1（由 Boot 管理） | 已锁定 | Session、CSRF、管理员标记认证 |
| MySQL | 8.0/8.4 LTS | 建议锁定 8.4 LTS | 用户与审计元数据；需在部署环境确认驱动兼容 |
| Kafka | 3.9.1 | 建议锁定 | 使用 Kafka 协议/客户端兼容矩阵；生产采用 3 broker |
| Kafka 客户端 | 3.9.1（Spring Boot 管理范围内） | 建议锁定 | 消费 offset、重试和死信测试 |
| Fluent Bit | 3.2.x（建议 3.2.10） | 建议锁定 | 验证 JSON parser、磁盘缓冲、Kafka output、TLS |
| Elasticsearch | **8.x（部署时固定具体补丁版本）** | 建议锁定 | 使用 8.x Data Stream、组件模板和 ILM；上线前在目标补丁版本完成 bulk、search_after、ILM 与故障压测 |
| Elasticsearch Java API Client | 与 ES 服务端同一 8.x minor | 必须同 minor | 禁止让客户端跨大版本漂移；验证 bulk、search_after、ILM |
| Kibana（可选） | 与 ES 完全同版本 | 建议 | 仅用于运维排障，不作为平台 UI |
| OpenTelemetry Java Agent | 2.15.x（建议 2.15.0） | 建议锁定 | 验证 Spring MVC/WebFlux、JDBC、Kafka、RabbitMQ、异步上下文 |
| OpenTelemetry Collector | 0.123.x（建议 0.123.0） | 建议锁定 | 验证 OTLP gRPC/HTTP、batch、memory_limiter、Tempo exporter |
| OpenTelemetry Collector Contrib | 与 Collector 同 0.123.x | 必须同 minor | 组件配置和 exporter 必须来自同一发行线 |
| Tempo | 2.7.x（建议 2.7.2） | 建议锁定 | 使用 OTLP 写入，查询 API 供平台聚合；对象存储需单独锁版本 |
| Grafana（可选） | 11.x | 建议 | 仅作运维视图；平台 Trace UI 不依赖 Grafana |
| Vue | 3.5.x | 建议锁定 | `/monitor` 前端 |
| Vite | 6.x | 建议锁定 | 前端构建 |
| Node.js | 22 LTS | 建议锁定 | CI 与本地构建一致 |

## 2. Elasticsearch 8.x 落地约束

本项目按 Elasticsearch 8.x 设计，不绑定某个具体补丁号。开发、预生产和生产环境应在部署清单中明确同一个 8.x 补丁版本（例如 `8.x.y`），并对该版本执行兼容性和容量压测。若后续变更 8.x 的 minor 或 patch，应重新验证 Data Stream/ILM、Java API Client、bulk 写入、查询分页和快照恢复。

版本选择建议：

1. 优先采用团队制品仓库中已批准、仍在维护的 8.x 版本。
2. Elasticsearch 服务端、Java API Client、Kibana（如启用）使用同一 minor，避免跨 minor 组合。
3. 容器镜像必须使用不可变 tag 或 digest，禁止 `latest`；将镜像摘要、插件和 JVM 参数纳入发布清单。

## 3. 兼容性测试矩阵

每次升级至少执行：

| 测试项 | 通过标准 |
|---|---|
| Kafka → Consumer → ES bulk | 300 EPS 设计峰值持续 30 分钟，消费延迟可恢复 |
| ES Data Stream/ILM | 第 8 天删除；模板字段类型稳定，无动态字段爆炸 |
| `search_after` 与 7 天窗口 | 普通用户只能当天，管理员最多 7 天 |
| OTel Agent → Collector → Tempo | Trace 完整、父子关系正确、错误 Span 可检索 |
| 日志/Trace 关联 | `trace.id`、`span.id` 关联成功率 100% |
| Kafka broker/ES 节点故障 | 无静默丢失；死信和告警可见 |
| TLS 与认证 | Kafka、ES、OTLP 生产连接均启用认证和 TLS |

## 4. 升级规则

- 只允许在依赖清单和容器镜像 tag 中声明版本，禁止使用 `latest`。
- ES 服务端和 Java API Client 先锁定同一 8.x minor，再单独验证 Spring Data/客户端适配。
- OTel Agent、Collector、Tempo 允许独立升级，但每次升级都要跑 Trace 关联和采样回归。
- 任何大版本升级必须新增 ADR，记录数据迁移、回滚和兼容窗口。
