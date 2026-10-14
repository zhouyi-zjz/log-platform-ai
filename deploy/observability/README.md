# OpenTelemetry 统一观测管线

## 平台指标

后端通过 Spring Boot Actuator 暴露 `/actuator/prometheus`、`/actuator/health` 和
`/actuator/metrics`。Prometheus 应从集群内访问后端 Service 的 8103 端口，并为
`log_platform_*` 指标配置告警。当前已提供入口接受/拒绝、Kafka broker 可用性、平台 Topic
离线/副本同步状态、消费 lag、消费批次与事件数、DLT 积压、ES Bulk 延迟/失败/幂等冲突、
查询延迟和异步导出创建/失败计数。Kafka broker 磁盘/JVM/请求延迟仍应通过 Strimzi/JMX Exporter
在生产监控栈补充，它们不替代应用视角的 Admin API 和消费组指标。

K3s 的 `45-backend-availability.yaml` 允许 `monitoring` 命名空间访问后端端口，
`kafka-servicemonitor.yaml` 同时抓取 API 与 Consumer Service。NetworkPolicy、metrics-server、
Prometheus Operator 对 ServiceMonitor/PrometheusRule 的 selector，以及 AlertmanagerConfig selector
仍必须在目标集群中验收，仓库没有把“清单可安装”冒充为通知已经送达。

## OpenTelemetry Java Agent

```bash
JAVA_TOOL_OPTIONS="-javaagent:/opt/otel/opentelemetry-javaagent.jar"
OTEL_SERVICE_NAME=billing-api
OTEL_RESOURCE_ATTRIBUTES="project.id=billing,deployment.environment=prod"
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
OTEL_PROPAGATORS=tracecontext,baggage
```

应用日志框架需要把当前 Span 的 trace/span id 写入 JSON 字段 `trace.id`、`span.id`。
Collector 的 `otlp/tempo` exporter 指向 Tempo 的 OTLP gRPC 接收端，环境变量使用
`TEMPO_OTLP_ENDPOINT=tempo:4317`。Trace 后端统一采用 ADR-001 已确定的 Tempo，
只维护 Tempo 单栈配置。

应用、Collector 和 Tempo 统一使用 W3C Trace Context。入口过滤器会校验 `traceparent`，
并在没有 Java Agent 时生成受约束的关联 ID；平台日志中的 `trace.id`、`span.id` 必须来自
当前 OTel Span 或该兼容层，才能在后续日志/Trace 联查中稳定关联。

## Collector 管线

`otel-collector-config.yaml` 同时定义三类信号：

- Trace 经过错误、慢请求和 10% 基线尾采样后写入 Tempo；
- Metric 通过 Prometheus exporter 暴露在 `:8889`，不使用租户、请求或运行 ID 作为标签；
- OTLP Log 写入 Loki，应用正文和凭据仍由业务侧脱敏策略控制。

Collector 自身的健康端点为 `:13133`。`deploy/k3s/.env` 设置
`INSTALL_OTEL_COLLECTOR=true` 后会部署可选 Collector；Tempo/Loki 必须由目标环境提供，
相关 endpoint 通过 Secret 或环境变量注入，不能写入业务代码。

告警规则的单一来源位于 Prometheus 原生格式的 `prometheus-rules.yaml`；
`render-prometheus-rule.sh` 在 K3s 安装时将其包装为 Prometheus Operator CRD。Kafka 分组覆盖自监控缺失/过期、集群不可达、broker 数下降、
离线分区、副本同步不足、HTTP 发布失败、Consumer 抓取缺失、lag 分级和消费停滞；其余规则覆盖
接入拒绝、Bulk 失败、查询延迟、Trace 完整率、Tempo 降级、FinOps 审计投影失败和 DLT 积压。
规则使用固定路由/状态标签，不携带项目、用户、请求或 Trace ID。

生产集群可应用 `kafka-alertmanager-config.yaml`，webhook URL 必须来自 `monitoring` 命名空间的
`log-platform-alerting` Secret。目标 Alertmanager 的 `alertmanagerConfigSelector` 需匹配
`alertmanagerConfig=log-platform`。`sendResolved=true` 会同步发送恢复通知。

## P0 本地联调栈

`docker-compose.p0.yml` 提供 Tempo、Loki、Collector、Prometheus、Alertmanager 和 Grafana 的可重复联调
拓扑，配置文件使用本目录中的固定版本。启动后执行 `./smoke-test.sh` 验证健康端点，再把后端
的 `OTEL_ENABLED=true`、`OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces` 和
`TEMPO_QUERY_ENABLED=true`、`KAFKA_MONITOR_ENABLED=true` 指向该栈。`alertmanager.yml` 中的
`host.docker.internal:5001` 只是本地 webhook 占位地址，联调时替换为本机接收器。生产环境不得
直接使用本地文件存储或该占位路由，必须切换到有认证、Snapshot 和多副本的受管观测组件。
