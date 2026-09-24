# K3s 部署

这套清单按“单节点 K3s、内部 Kafka/Elasticsearch、前端 Nginx 反向代理”的基线提供部署路径，包含：

- 前端 Vue 构建和 Nginx 镜像；
- Spring Boot 后端镜像；
- MySQL 8.4 用户库；
- Kafka 3.9.1 单节点 KRaft；
- Elasticsearch 8.15.3 单节点和日志 Data Stream 初始化；
- 可选的 Fluent Bit DaemonSet；
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
- `KAFKA_REPLICAS=1` 和 `KAFKA_REPLICATION_FACTOR=1` 是单节点基线，脚本会拒绝其他值。生产 Kafka 集群应改用 Kafka Operator（例如 Strimzi）或经过评审的多节点 KRaft 清单，并同步调整 quorum、广告地址、副本和 ISR。
- `ELASTICSEARCH_IMAGE` 使用单节点模式且关闭 ES 内置安全，仅适合 K3s 内网基线。生产应配置 ES TLS、认证、快照和多节点拓扑，并同步修改后端的 HTTPS/认证配置。
- MySQL 的 [base.sql](../../sql/base.sql) 只会在空数据目录第一次初始化时执行。修改用户或 schema 后请使用数据库迁移流程，不要删除 PVC 重建生产数据。
- `INSTALL_FLUENT_BIT=true` 时会在每个 K3s 节点部署 DaemonSet，从节点 `/var/log/apps/*.log` 读取 JSON 并写入内部 Kafka。应用日志必须包含 `project.id`；需要采集容器 stdout 时，应根据集群日志路径另行调整 Input。

## 验收

```bash
kubectl -n log-platform get pods,svc,ingress,pvc
kubectl -n log-platform logs job/kafka-topic-init-<deployment-id>
kubectl -n log-platform logs job/elasticsearch-init-<deployment-id>
deploy/k3s/smoke-test.sh deploy/k3s/.env
```

验收脚本会通过前端 Service 检查 Nginx、CSRF，并向后端提交一条 Kafka 日志。HTTP `202` 只代表 Kafka 接收成功；还应登录 UI 查询 Elasticsearch，确认消费、Bulk 写入和权限窗口均正常。

## 升级和清理

修改代码后递增 `IMAGE_TAG`，再运行安装脚本即可滚动更新前后端。不要在生产环境使用 `kubectl delete pvc`。

```bash
CONFIRM=DELETE deploy/k3s/uninstall.sh log-platform
```

该命令会删除整个命名空间及其 PVC，只有在明确需要销毁环境时执行。

## 生产扩展

当前清单是可运行基线，不是完整高可用方案。投入生产前至少应完成：Kafka/ES 多节点和副本、加密连接、镜像签名/私有仓库、备份恢复演练、Prometheus/告警、Ingress 证书自动续期、查询限流的 Redis/网关化，以及按照 [容量模型](../../docs/capacity/log-platform-capacity-model.md) 调整 PVC 和资源请求。
