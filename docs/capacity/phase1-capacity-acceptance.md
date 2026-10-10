# Phase 1 容量验收记录模板

## 目的

本文件定义 Phase 1 的可重复验收口径。仓库提供的
[`deploy/k3s/capacity/ingest-load.sh`](../../deploy/k3s/capacity/ingest-load.sh) 只负责生成
HTTP 入口压力和统计响应，不会自动证明 Kafka、Elasticsearch 或应用已经达到目标吞吐。

## 当前事实状态

| 项目 | 状态 | 说明 |
|---|---|---|
| 入口/消费/Bulk/查询/导出指标 | 已实现 | 通过 Actuator/Prometheus 端点暴露 |
| Redis Session/分布式限流开关 | 已实现基线 | K3s Redis 单副本；Redis HA 待验证 |
| HPA/PDB/NetworkPolicy | 已实现清单 | metrics-server、监控抓取和策略效果待验证 |
| 300/450 EPS 持续吞吐 | 未验证 | 没有真实 Kafka/ES 集群压测证据 |
| 600 EPS 突发 | 未验证 | 仅为设计验收目标 |
| 24 小时稳定性与故障恢复 | 未验证 | 需要生产等价环境和演练记录 |

## 推荐执行矩阵

1. 使用生产等价 mapping、刷新周期、磁盘和资源限制，先执行 60、150、300、450 EPS 各 30 分钟。
2. 执行 600 EPS 10 分钟突发，再执行 300 EPS 24 小时稳定性测试。
3. 写入期间保持 1、5、10、20 并发查询，覆盖 15 分钟、24 小时和 7 天窗口，以及精确过滤、全文、Trace 和聚合。
4. 分别重启 Kafka broker、Consumer、Elasticsearch 节点和后端 Pod，记录 Kafka lag、ES rejection、DLT、入口 5xx 和端到端可见延迟。
5. 以 Prometheus 指标、Kafka offset、ES 文档计数和抽样事件 ID 对账，禁止只用 HTTP 202 作为“已写入”证据。

示例输入工具命令：

```bash
TARGET_URL=https://logs.example.com/monitor/api/logs/ingest \
TOKEN="$LOG_INGEST_TOKEN" PROJECT_ID=billing EPS=300 DURATION_SECONDS=1800 \
deploy/k3s/capacity/ingest-load.sh
```

## 通过标准

- 450 EPS 持续 60 分钟、600 EPS 突发 10 分钟无未解释丢失；
- HTTP 入口错误率低于 0.1%，ES rejection 低于 1%；
- 端到端可见延迟 P95 小于 5 秒、P99 小于 15 秒；
- 混合查询下当天检索 P95 小于 2 秒、7 天检索 P95 小于 5 秒；
- Kafka、ES、后端或 Redis 故障恢复后积压可回落，DLT 有明确处理记录；
- 报告附带资源规格、事件大小分布、mapping、刷新策略、客户端重试策略和完整原始指标。

在上述条件完成前，`300 EPS`、`450 EPS` 和 `600 EPS` 都只能作为规划目标，不能写入生产 SLA。
