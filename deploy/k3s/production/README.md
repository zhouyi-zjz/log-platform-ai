# 生产拓扑门禁

`deploy/k3s` 目录中的 MySQL、Kafka、Elasticsearch 和 Redis 清单是单节点联调基线，不能通过
调整副本数直接升级为生产 HA。生产环境必须先安装并验收对应 Operator 或使用受管服务，再把
应用环境变量指向生产端点。

## 必选依赖

- Kafka Operator（推荐 Strimzi）：3 Broker、RF=3、`min.insync.replicas=2`、TLS/SASL、Topic ACL。
- Elasticsearch Operator（推荐 ECK）：3 个 data/ingest 节点、TLS、认证、Snapshot 和 ILM。
- MySQL 受管 HA 或经过演练的主从/组复制，应用账号只能访问业务库。
- Redis Sentinel/Cluster 或受管 Redis，启用 TLS/ACL，Session 和分布式限流使用独立命名空间。
- Prometheus Operator、Alertmanager、Grafana、Tempo 和 Loki 的生产实例。

## 参考清单

- [`strimzi-kafka.yaml`](strimzi-kafka.yaml)：Kafka 生产 CR 参考。
- [`eck-elasticsearch.yaml`](eck-elasticsearch.yaml)：Elasticsearch 生产 CR 参考。
- [`redis-ha-values.yaml`](redis-ha-values.yaml)：Redis HA Helm Values 参考。
- [`production-gate.sh`](production-gate.sh)：部署前门禁，防止误把单节点清单当作生产集群。

## 应用切换

应用发布前需要完成：

1. Kafka Topic 和 DLT 已创建，`finops.budget-audit.v1` 仅允许 FinOps Producer，日志平台只读消费。
2. ES 模板、ILM、Snapshot 和审计索引已验证。
3. `OTEL_ENABLED=true`、`TEMPO_QUERY_ENABLED=true`、`INSTALL_PROMETHEUS_RULES=true` 经过真实联调。
4. `FINOPS_BUDGET_AUDIT_CONSUMER_ENABLED=true` 只在 Consumer Deployment 开启。
5. 备份恢复、滚动升级、网络分区和容量验收报告已归档。
