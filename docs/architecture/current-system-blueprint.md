# 当前系统功能蓝图与链路完整度

> 评估基线：2026-10-10。本文只描述仓库当前可见的代码、前端、配置和 K3s 清单，不把设计文档中的目标状态当作已交付能力。
>
> 完整度是工程交付口径，不是运行时成功率或 SLA：`75%+` 表示主干代码、接口/界面、部署基线和自动化验证大部分具备，但仍有真实环境联调、HA 或运维门禁缺口；`50%~74%` 表示可用主干存在，但有明显入口、部署或验证缺口；`<50%` 表示设计/代码片段存在，尚不能作为稳定产品能力。

## 1. 统一图例与边界

| 标记 | 含义 |
|---|---|
| `已实现` | 代码和对应服务接口存在，并有单元/上下文测试或构建证据 |
| `基线可部署` | K3s、初始化 Job 或配置已纳入仓库，可用于单节点联调 |
| `待联调` | 需要真实 Kafka、Elasticsearch、Tempo、Redis 或 Prometheus 环境验证 |
| `部分完整` | 主干可用，但前端入口、HA、备份恢复、故障演练或生产级安全仍缺失 |
| `未实现` | 当前只有规划、扩展点或没有实现代码 |

系统边界是“日志平台 + Trace 关联 + 只读 FinOps 审计投影”。日志平台不负责 Token 计量、价格计算、预算预占、模型调用拦截或成本结算；这些职责属于独立 FinOps 服务。

## 2. 当前系统功能蓝图

```mermaid
flowchart TB
    subgraph Sources[信号与使用者]
        APP[业务应用 JSON 日志]
        AGENT[Fluent Bit / Vector]
        USER[平台用户]
        OP[平台管理员]
        FINOPS[独立 FinOps 服务]
    end

    subgraph Edge[接入与安全边界]
        INGRESS[Traefik Ingress]
        NGINX[Nginx 前端]
        AUTH[登录 / Session / CSRF / BCrypt]
        TOKEN[全局兼容 Token / 项目 Token]
        ACL[项目 ACL 与时间窗口]
    end

    subgraph Product[产品能力层]
        INGEST[HTTP 日志采集]
        SEARCH[日志检索]
        ANALYSIS[白名单聚合分析]
        DETAIL[详情与上下文]
        TRACE[日志 / Tempo Trace 联查]
        EXPORT[异步 CSV 导出]
        ADMIN[项目 Token 管理]
        DLT[DLT 查询 / 单条重放]
        BA[Budget Audit 查询]
    end

    subgraph Processing[数据处理层]
        PRODUCER[Kafka Producer<br/>broker ack]
        CONSUMER[日志 Consumer<br/>批量归一化 / 稳定 ID]
        WRITER[ES Bulk create<br/>409 幂等]
        FINOPS_CONSUMER[FinOps Audit Consumer<br/>Schema / metadata-only]
        METRICS[Actuator / Micrometer]
    end

    subgraph Data[数据与外部观测栈]
        MYSQL[(MySQL<br/>用户 / 项目 / 授权 / 审计)]
        REDIS[(Redis<br/>Session / 分布式限流)]
        KAFKA[(Kafka<br/>logs.raw / DLT / budget-audit)]
        ES[(Elasticsearch<br/>logs Data Stream + 7d ILM)]
        FINOPS_ES[(finops-budget-audit-v1)]
        TEMPO[(Tempo<br/>Trace 查询)]
        OTEL[OTel Collector]
        PROM[Prometheus / Grafana / Loki]
    end

    APP --> AGENT --> KAFKA
    APP --> INGEST
    USER --> INGRESS --> NGINX --> AUTH
    OP --> INGRESS
    AUTH --> MYSQL
    AUTH -. K3s 多副本开关 .-> REDIS
    INGEST --> TOKEN --> PRODUCER --> KAFKA
    AUTH --> ACL
    SEARCH --> ACL --> MYSQL
    SEARCH --> ES
    ANALYSIS --> SEARCH
    DETAIL --> SEARCH
    TRACE --> ACL
    TRACE --> ES
    TRACE --> TEMPO
    EXPORT --> SEARCH
    ADMIN --> TOKEN
    ADMIN --> MYSQL
    DLT --> KAFKA
    DLT --> MYSQL
    BA --> ACL
    BA --> FINOPS_ES
    KAFKA --> CONSUMER --> WRITER --> ES
    KAFKA -->|budget-audit topic| FINOPS_CONSUMER --> FINOPS_ES
    FINOPS --> KAFKA
    FINOPS_CONSUMER -. 非法事件 .-> KAFKA
    Processing -. 指标 .-> METRICS --> PROM
    APP -. OTLP .-> OTEL --> TEMPO
    OTEL --> PROM

    classDef done fill:#e7f6ec,stroke:#26834a,color:#163b24;
    classDef baseline fill:#eef3ff,stroke:#4263eb,color:#1f2d5c;
    classDef verify fill:#fff4d6,stroke:#b7791f,color:#5f4314;
    classDef partial fill:#fff0f0,stroke:#c53030,color:#651b1b;
    classDef external fill:#f1f3f5,stroke:#868e96,color:#343a40;
    class AUTH,TOKEN,ACL,INGEST,SEARCH,ANALYSIS,DETAIL,TRACE,PRODUCER,CONSUMER,WRITER,ADMIN,DLT,BA done;
    class INGRESS,NGINX,MYSQL,REDIS,KAFKA,ES,FINOPS_ES,OTEL baseline;
    class EXPORT,FINOPS_CONSUMER,METRICS,TEMPO,PROM partial;
    class APP,AGENT,USER,OP,FINOPS external;
```

### 能力分层结论

- **接入层**：支持 Fluent Bit 直写 Kafka 和 HTTP JSON 采集；HTTP 接口在 broker ack 后返回 `202`，不承诺 Elasticsearch 已可查。
- **处理层**：日志以 `logs.raw` 为主通道，Consumer 做字段归一化、Trace Context 提取、稳定事件 ID 和 ES 幂等写入；失败进入 DLT。
- **产品层**：登录、查询、分析、详情、上下文、Trace 瀑布图已在 `App.vue` 中形成主工作台；导出、FinOps、DLT 只有后端或运维入口。
- **数据层**：MySQL 存身份和授权，Redis 支持多副本 Session/限流开关，Kafka 解耦写入，Elasticsearch 保存 7 天日志，Tempo 保存 Trace。
- **外部依赖**：Tempo、Prometheus、Loki 和独立 FinOps 服务不由本仓库完整托管，启用与否由环境变量和外部部署决定。

## 3. 产品能力功能图

```mermaid
flowchart LR
    PLATFORM[LogScope 日志平台]

    subgraph Observe[观测工作台]
        O1[登录与当前用户<br/>已实现 85%]
        O2[条件检索 / 游标翻页<br/>已实现 90%]
        O3[日志详情 / 脱敏<br/>已实现 85%]
        O4[上下文窗口<br/>已实现 80%]
        O5[级别 / 服务 / 环境聚合<br/>已实现 80%]
        O6[Trace 瀑布与关联日志<br/>部分完整 60%]
    end

    subgraph Ingest[采集与处理]
        I1[HTTP JSON 入口<br/>已实现 80%]
        I2[Fluent Bit 文件采集<br/>基线可部署 75%]
        I3[Kafka 缓冲 / 重试 / DLT<br/>部分完整 65%]
        I4[归一化 / 稳定 ID / ES 幂等<br/>已实现 80%]
    end

    subgraph Govern[治理与运维]
        G1[项目 ACL / 时间窗<br/>已实现 85%]
        G2[项目 Token 轮换<br/>已实现 75%]
        G3[异步 CSV 导出<br/>后端 60%]
        G4[查询审计 / 字段脱敏<br/>已实现 75%]
        G5[指标 / 告警接入点<br/>部分完整 50%]
        G6[DLT 管理 / 重放<br/>后端 55%]
    end

    subgraph Finops[FinOps 边界]
        F1[Budget Audit v1 Schema<br/>已实现 65%]
        F2[独立 ES 审计投影<br/>部分完整 55%]
        F3[预算裁决 / 成本计算<br/>不在本系统 0%]
    end

    subgraph Future[规划能力]
        X1[保存查询 / 分享<br/>未实现]
        X2[项目角色 / 配额<br/>未实现]
        X3[AI 摘要 / 根因 / 自然语言查询<br/>未实现]
        X4[备份恢复 / 故障演练<br/>未实现]
    end

    PLATFORM --> Observe
    PLATFORM --> Ingest
    PLATFORM --> Govern
    PLATFORM --> Finops
    PLATFORM --> Future
    O1 --> O2 --> O3 --> O4 --> O6
    O2 --> O5
    I1 --> I3 --> I4
    I2 --> I3
    G1 --> O2
    G1 --> O3
    G1 --> O4
    G1 --> O6
    G4 --> O2
    F1 --> F2

    classDef full fill:#e7f6ec,stroke:#26834a,color:#163b24;
    classDef partial fill:#fff4d6,stroke:#b7791f,color:#5f4314;
    classDef missing fill:#fff0f0,stroke:#c53030,color:#651b1b;
    class O1,O2,O3,O4,O5,I1,I4,G1,G2,G4,F1 full;
    class O6,I2,I3,G3,G5,G6,F2 partial;
    class F3,X1,X2,X3,X4 missing;
```

前端证据在 [App.vue](../../log-platform-ui/src/App.vue) 和 [platform.js](../../log-platform-ui/src/api/platform.js)：检索、聚合、详情、上下文和 Trace 已调用后端接口；导出 API 虽然存在，但当前工作台没有导出按钮和任务轮询入口。管理员项目 Token、DLT、Budget Audit 和 Trace 完整率接口属于后端/运维入口，不应误记为已完成的前端产品模块。

## 4. 当前 K3s 系统拓扑图

```mermaid
flowchart TB
    BROWSER[浏览器]
    COLLECTOR_CLIENT[业务应用 / Fluent Bit DaemonSet]
    FINOPS_EXT[独立 FinOps 服务]

    subgraph K3S[log-platform 命名空间：单节点联调基线]
        ING[Traefik Ingress]
        FE[Frontend Deployment<br/>Nginx + Vue]
        API[Backend Deployment<br/>8103 / API、Session、Query]
        WORKER[Consumer Deployment<br/>8104 / Kafka Consumer、FinOps Consumer]
        INIT1[Kafka Topic Init Job]
        INIT2[Elasticsearch Init Job<br/>Template / ILM / Data Stream]
        FB[Fluent Bit DaemonSet]
        OTELC[OTel Collector Deployment<br/>可选]

        MYSQL[(MySQL StatefulSet<br/>1 Pod)]
        REDIS[(Redis StatefulSet<br/>1 Pod)]
        KAFKA[(Kafka KRaft StatefulSet<br/>默认 1 Pod / 6 分区)]
        ES[(Elasticsearch StatefulSet<br/>1 Pod / single-node)]
    end

    subgraph External[外部或可选观测栈]
        TEMPO[Tempo]
        LOKI[Loki]
        PROM[Prometheus / Grafana]
    end

    BROWSER --> ING --> FE
    FE --> API
    COLLECTOR_CLIENT --> FB --> KAFKA
    COLLECTOR_CLIENT --> API
    API --> MYSQL
    API --> REDIS
    API --> KAFKA
    API --> ES
    API -. Trace query .-> TEMPO
    KAFKA --> WORKER --> ES
    WORKER --> MYSQL
    WORKER --> KAFKA
    FINOPS_EXT --> KAFKA
    KAFKA --> WORKER
    API -. OTLP .-> OTELC --> TEMPO
    OTELC --> LOKI
    OTELC --> PROM
    API -. metrics .-> PROM
    INIT1 --> KAFKA
    INIT2 --> ES

    classDef entry fill:#e7f6ec,stroke:#26834a,color:#163b24;
    classDef app fill:#eef3ff,stroke:#4263eb,color:#1f2d5c;
    classDef data fill:#fff4d6,stroke:#b7791f,color:#5f4314;
    classDef external fill:#f1f3f5,stroke:#868e96,color:#343a40;
    classDef gap fill:#fff0f0,stroke:#c53030,color:#651b1b;
    class BROWSER,COLLECTOR_CLIENT,FINOPS_EXT entry;
    class ING,FE,API,WORKER,FB,OTELC,INIT1,INIT2 app;
    class MYSQL,REDIS,KAFKA,ES data;
    class TEMPO,LOKI,PROM external;
```

### 拓扑判断

| 区域 | 当前状态 | 主要缺口 |
|---|---|---|
| API / Consumer 解耦 | 已落地 | Consumer 多副本扩展和分区再均衡尚未做真实滚动升级验证 |
| Kafka | 单节点 KRaft 基线 | 生产建议 3 broker、认证/TLS、副本因子和故障演练 |
| Elasticsearch | 单节点、关闭安全认证、7 天 ILM | 生产集群、认证/TLS、快照恢复、容量压测 |
| MySQL | StatefulSet 单 Pod | HA、备份恢复、迁移演练 |
| Redis | StatefulSet 单 Pod，API 开启共享 Session/限流 | Redis HA、故障转移和多副本 Session 验证 |
| OTel / Tempo / Loki / Prometheus | Collector 清单和配置存在，后端按开关连接 | 外部后端部署、抓取、告警通知和完整 Trace 联调 |

## 5. 业务链路图

### 5.1 日志采集到可检索

```mermaid
sequenceDiagram
    autonumber
    participant APP as 业务应用
    participant FB as Fluent Bit / HTTP API
    participant API as Backend API
    participant K as Kafka logs.raw
    participant C as Consumer
    participant ES as Elasticsearch
    participant DLT as logs.raw.DLT

    alt 采集器直写
        APP->>FB: JSON 日志
        FB->>K: produce(key, event)
    else HTTP 兼容入口
        APP->>API: POST /monitor/api/logs/ingest + Token
        API->>API: JSON / 大小 / Token 校验
        API->>K: send + broker ack
        K-->>API: partition + offset
        API-->>APP: 202 Accepted
    end
    K->>C: poll batch
    C->>C: 归一化字段、提取 Trace、生成稳定文档 ID
    C->>ES: Bulk create(logs-platform-default)
    alt 成功或重复 create
        ES-->>C: 2xx 或 409 幂等冲突
        C->>K: 提交 offset
    else 重试后仍失败
        C->>DLT: 失败事件 + 原始元数据
        C->>K: 提交原始消息 offset
    end
```

**链路完整度：80%（主干已实现，真实 Kafka/ES 联调与故障恢复待验证）**。

### 5.2 用户检索到故障定位

```mermaid
flowchart LR
    U[用户登录] --> S[Session / CSRF]
    S --> A[项目 ACL + 时间窗口]
    A --> Q[日志搜索 API]
    Q --> E[(Elasticsearch)]
    E --> R[结果列表 / search_after]
    R --> D[详情 + 字段脱敏]
    D --> C[前后文查询]
    D --> T[Trace ID]
    T --> BOTH[日志与 Tempo 联查]
    BOTH --> WF[Trace 瀑布图]
    R --> AGG[级别 / 服务 / 环境聚合]
    Q --> AUDIT[查询审计]

    classDef full fill:#e7f6ec,stroke:#26834a,color:#163b24;
    classDef partial fill:#fff4d6,stroke:#b7791f,color:#5f4314;
    class U,S,A,Q,E,R,D,C,AGG,AUDIT full;
    class T,BOTH,WF partial;
```

**链路完整度：82%（查询闭环成熟；PIT 一致快照、ES `timed_out` 处理和真实 Tempo 数据仍有缺口）**。

### 5.3 FinOps Budget Audit 只读投影

```mermaid
flowchart LR
    AG[Agent Platform / Model Gateway] --> F[FinOps 服务]
    F -->|Budget Audit v1| K[(Kafka ai.budget.audit.v1)]
    K --> N[Schema / metadata-only 校验]
    N --> P[BudgetAuditNormalizer]
    P --> I[(finops-budget-audit-v1)]
    I --> API[Budget Audit 查询 API]
    API --> ACL[项目 ACL]
    N -. 非法事件 .-> DLT[FinOps DLT]

    classDef full fill:#e7f6ec,stroke:#26834a,color:#163b24;
    classDef partial fill:#fff4d6,stroke:#b7791f,color:#5f4314;
    classDef external fill:#f1f3f5,stroke:#868e96,color:#343a40;
    class AG,F external;
    class K,N,P,I,API,ACL full;
    class DLT partial;
```

**链路完整度：55%（Schema、Normalizer、独立索引和查询代码存在；Consumer 默认关闭，真实 FinOps/Kafka/ES 联调及前端入口缺失）**。

## 6. 链路模块完整度清单

| 链路模块 | 估算完整度 | 仓库证据 | 当前阻断项 |
|---|---:|---|---|
| 登录、Session、CSRF、项目 ACL | 85% | `LoginController`、`SecurityConfig`、`ProjectAuthorizationService`、MySQL Schema、前端登录 | 登录风控、Redis HA、项目级角色/配额 |
| HTTP 采集 | 80% | `LogIngestController`、Token 校验、payload 限制、Kafka ack、部署环境变量 | 真实 Kafka ack/超时与压测 |
| Fluent Bit 采集 | 75% | `deploy/fluent-bit/*`、`90-fluent-bit.yaml`、磁盘 buffer | 容器 stdout 自动发现、跨节点断链演练 |
| Kafka 缓冲、重试、DLT | 65% | `LogIngestConfiguration`、Topic Init Job、DLT Service | 单节点、无 TLS/ACL 基线、无故障演练 |
| 归一化与 ES 幂等写入 | 80% | `LogEventNormalizer`、`LogKafkaConsumer`、`ElasticsearchLogWriter`、单元测试 | 真实 Bulk、mapping、积压恢复和容量证据 |
| 查询、分页、聚合、上下文 | 85% | `LogQueryController`、白名单 DSL、`search_after`、前端工作台 | 无 PIT；部分 ES 超时响应仍可能透传 |
| 详情脱敏与查询审计 | 75% | `LogFieldRedactor`、`LogQueryAuditService`、详情/审计测试 | 导出审计和更细粒度数据治理 |
| Trace 联查与瀑布图 | 60% | `TempoTraceService`、`TraceCorrelationController`、`App.vue` Trace drawer | Tempo 默认关闭；真实 OTel/Tempo 数据和完整率验证 |
| 异步 CSV 导出 | 60% | `LogExportService`、任务状态/下载 API、有界 Worker | 前端无入口；结果在 JVM 内存；多副本和对象存储未实现 |
| 项目 Token 管理 | 75% | `ProjectAdminController`、`ProjectTokenService`、轮换 API | 前端管理页缺失；密钥分发与审计操作闭环待补 |
| DLT 管理与单条重放 | 55% | `DltAdminController`、`DltReplayService`、审计与指标 | 无前端管理页；真实 Kafka offset/幂等核对待验证 |
| FinOps Budget Audit | 55% | Schema、`FinopsAuditConsumer`、`BudgetAuditNormalizer`、独立索引/查询 | 默认关闭；外部 FinOps 合约和联调未完成 |
| 指标、Collector、告警接入 | 50% | Actuator、Micrometer、Collector/Prometheus 配置、Runbook | Prometheus 抓取、Grafana 面板、通知渠道和告警演练 |
| K3s 部署与可用性基线 | 60% | API/Consumer Deployment、PDB、HPA、NetworkPolicy、Smoke Test | Kafka/ES/MySQL/Redis 均单节点；备份恢复与故障演练 |
| 保存查询、分享、AI 分析 | 0% | 当前无实现代码 | 产品需求尚未进入交付范围 |

## 7. 结论与验收优先级

当前系统可以作为**功能型日志检索 MVP 和单节点联调基线**：采集、Kafka 缓冲、归一化、ES 查询、权限边界和 Trace 关联的主链路已经具备。不能将其表述为生产级可观测平台，原因是 HA、容量、备份恢复、真实观测栈联调和告警闭环尚未形成证据。

建议按以下顺序补齐：

1. 用真实 Kafka、ES、Redis、Tempo 环境跑通写入、查询、Trace、DLT 和滚动升级验收。
2. 补前端导出、项目管理、DLT 和 Budget Audit 入口，并让接口权限与审计可见。
3. 完成 Kafka/ES/MySQL/Redis HA、TLS/ACL、快照恢复、容量压测和故障演练。
4. 接入 Prometheus/Grafana/Alertmanager，建立消费积压、ES 写入失败、查询超时、Trace 完整率和 DLT 积压告警。

## 8. 事实来源

- [当前架构与容量基线](current-architecture-and-blueprint.md)
- [功能架构](functional-architecture.md)
- [后端控制器](../../log-platform-ai-admin/src/main/java/com/zhouyi/admin/controller)
- [后端服务](../../log-platform-ai-admin/src/main/java/com/zhouyi/admin/service)
- [前端工作台](../../log-platform-ui/src/App.vue)
- [前端 API](../../log-platform-ui/src/api/platform.js)
- [K3s 部署清单](../../deploy/k3s/manifests)
- [Fluent Bit 配置](../../deploy/fluent-bit)
