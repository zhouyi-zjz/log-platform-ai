# K3s 部署

这套清单按“K3s、内部双节点 Kafka/Elasticsearch、前端 Nginx 反向代理”的基线提供部署路径，包含：

- 前端 Vue 构建和 Nginx 镜像；
- Spring Boot 后端镜像；
- 独立的日志 API 与 Kafka/Elasticsearch Consumer Deployment；
- MySQL 8.4 用户库；
- Kafka 3.9.1 双节点 KRaft（2 个 broker/controller、Topic 副本因子 2）；
- Elasticsearch 8.15.3 双节点集群和日志 Data Stream 初始化；
- Redis 单副本 StatefulSet，用于多副本 Session 和可选的分布式查询限流；
- 后端 PDB、HPA、NetworkPolicy 和 Actuator/Prometheus 端点；
- 可选的 Fluent Bit DaemonSet；
- 可选的 OpenTelemetry Collector Deployment，统一转发 Trace/Metric/Log；
- K3s Ingress、PVC、Secret、Kafka Topic、ES ILM/索引模板和验收脚本。

## 前置条件

1. 已安装并可访问 K3s 集群，且默认存在 `local-path` StorageClass 和 Traefik IngressClass。
2. 部署机器能运行 `kubectl`、`envsubst` 和 Docker；集群节点能拉取基础镜像。
3. 多节点集群应使用团队的镜像仓库。单节点开发集群可使用 `LOAD_IMAGES=true` 将应用镜像导入 K3s containerd。
4. 生产环境必须为域名准备 TLS Secret。当前 ES 清单只在集群内部暴露 HTTP，不应把 Elasticsearch Service 暴露到公网。

## 快速部署

```bash
cd /path/to/log-platform-ai
cp deploy/k3s/.env.example deploy/k3s/.env
${EDITOR:-vi} deploy/k3s/.env

# 先检查 K3s 节点、StorageClass 和 IngressClass
deploy/k3s/preflight.sh

# 使用镜像仓库（多节点推荐）
PUSH_IMAGES=true deploy/k3s/install.sh deploy/k3s/.env

# 或单节点 K3s + 本机 Docker/containerd
# 将 IMAGE_REPOSITORY 改为本机镜像名，并设置 LOAD_IMAGES=true、PUSH_IMAGES=false
LOAD_IMAGES=true deploy/k3s/install.sh deploy/k3s/.env
```

如果启用 HTTPS，先创建证书 Secret：

```bash
kubectl -n log-platform create secret tls log-platform-tls \
  --cert=/path/to/fullchain.pem --key=/path/to/privkey.pem
```

然后访问 `https://<APP_HOST>/`。没有 TLS 的本地联调需将 `.env` 中的
`SESSION_COOKIE_SECURE=false`，并自行移除或调整 Ingress 的 `tls` 段。

## 配置说明

- `DB_PASSWORD` 和 `MYSQL_ROOT_PASSWORD` 必须改成随机强密码；后端使用 `DB_USERNAME` 对应的专用账号。
- `LOG_INGEST_TOKEN` 必须设置。空 Token 会让 HTTP 日志接入接口失去鉴权。
  已迁移到项目独立 Token 后可清空该兼容值；独立 Token 通过管理员 API 轮换，数据库只保存摘要。
- `BOOTSTRAP_ADMIN_USERNAME` 和 `BOOTSTRAP_ADMIN_PASSWORD` 必须使用非占位值；密码至少 12 位。
  应用每次启动都从 Secret 创建或同步该管理员，因此旧版固定密码也会被覆盖；SQL 和镜像中不保存默认登录密码。
- 后端在 K3s 中设置 `SESSION_STORE_TYPE=redis` 和 `LOG_QUERY_DISTRIBUTED_RATE_LIMIT=true`，会话与查询限流依赖 Redis Service；Redis 当前只有 1 个副本和 1 个 PVC，只是共享状态基线，不是 Redis HA。无 Redis 的本地开发/测试默认使用进程内 Session 和限流。
- `/actuator/health`、`/actuator/metrics` 和 `/actuator/prometheus` 已暴露给集群内部监控；HPA 需要集群已安装 `metrics-server`，Prometheus 需要允许访问后端 8103 端口。
- API 与 Consumer 已拆成两个 Deployment：API 的 `LOG_CONSUMER_ENABLED=false`，Consumer 使用独立副本数和无 Service 暴露的 8104 端口，二者共享 Kafka consumer group 以按分区扩展消费。
- `deploy/observability/prometheus-rules.yaml` 提供接入拒绝率、Bulk 失败、查询 P95、Trace 完整率和 Tempo 降级告警；需要在目标 Prometheus Operator 中安装后才会生效。
- `INSTALL_PROMETHEUS_RULES=true` 时安装上述 `PrometheusRule`；目标集群必须已有 Prometheus Operator 和 `monitoring` 命名空间，否则保持关闭并由监控团队单独应用规则。
- `INSTALL_OTEL_COLLECTOR=true` 时会额外部署 `otel-collector` Service。Collector 只负责信号转发和尾采样，Tempo/Loki 仍需由目标观测栈提供；`OTEL_ENABLED=true` 后后端通过 OTLP HTTP 发送 Trace，Collector 或下游不可用不应阻塞日志接入。
- 异步导出接口 `POST /monitor/api/logs/exports` 复用查询 ACL，单任务默认最多 10,000 行/50 MiB，结果在应用内存保留 30 分钟；多副本生产环境仍需迁移持久化任务队列和对象存储。
- `KAFKA_REPLICAS=2`、`KAFKA_REPLICATION_FACTOR=2` 和 `ES_REPLICAS=2` 是本清单的固定双实例拓扑，安装脚本会拒绝其他值。两个 KRaft controller 不能容忍一台节点故障后继续选主；需要更高可用性时应改用生产 Operator（例如 Strimzi/ECK）并使用奇数节点。
- `ELASTICSEARCH_IMAGE` 使用双节点发现模式且关闭 ES 内置安全，仅适合 K3s 内网基线。生产应配置 ES TLS、认证、快照和更完整的节点角色拓扑，并同步修改后端的 HTTPS/认证配置。
- 安装脚本会通过 `mysql-schema-<deployment-id>` Job 幂等执行 [base.sql](../../sql/base.sql)，
  为已有 PVC 补充项目权限表。后续复杂迁移仍应引入 Flyway/Liquibase，不要删除生产 PVC 重建。
- `INSTALL_FLUENT_BIT=true` 时会在每个 K3s 节点部署 DaemonSet，从节点 `/var/log/apps/*.log` 读取 JSON 并写入内部 Kafka。应用日志必须包含 `project.id`；需要采集容器 stdout 时，应根据集群日志路径另行调整 Input。

## 验收

```bash
kubectl -n log-platform get pods,svc,ingress,pvc
kubectl -n log-platform logs job/kafka-topic-init-<deployment-id>
kubectl -n log-platform logs job/elasticsearch-init-<deployment-id>
kubectl -n log-platform logs job/mysql-schema-<deployment-id>
deploy/k3s/smoke-test.sh deploy/k3s/.env
```

验收脚本会通过前端 Service 检查 Nginx、CSRF，并向后端提交一条 Kafka 日志。HTTP `202`
代表 Kafka broker 已确认消息；还应登录 UI 查询 Elasticsearch，确认消费、Bulk 写入、幂等和项目权限均正常。

普通用户访问项目前，先登记项目并授权：

```sql
INSERT INTO log_project (project_id, project_name) VALUES ('billing', '计费项目');
INSERT INTO sys_user_project (user_id, project_id) VALUES (<user-id>, 'billing');
```

## 升级和清理

修改代码后递增 `IMAGE_TAG`，再运行安装脚本即可滚动更新前后端。不要在生产环境使用 `kubectl delete pvc`。

```bash
CONFIRM=DELETE deploy/k3s/uninstall.sh log-platform
```

该命令会删除整个命名空间及其 PVC，只有在明确需要销毁环境时执行。

## 生产扩展

当前清单是可运行的双实例基线，不是完整高可用方案。投入生产前至少应完成：Kafka 奇数节点和副本、ES 多数据节点、加密连接、镜像签名/私有仓库、备份恢复演练、Prometheus/告警、Ingress 证书自动续期、Redis HA、导出持久化队列/对象存储，以及按照 [容量模型](../../docs/capacity/log-platform-capacity-model.md) 调整 PVC 和资源请求。
