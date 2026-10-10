# OpenTelemetry 统一观测管线

## 平台指标

后端通过 Spring Boot Actuator 暴露 `/actuator/prometheus`、`/actuator/health` 和
`/actuator/metrics`。Prometheus 应从集群内访问后端 Service 的 8103 端口，并为
`log_platform_*` 指标配置告警。当前已提供入口接受/拒绝、Kafka 消费批次与事件数、
ES Bulk 延迟/失败/幂等冲突、查询延迟和异步导出创建/失败计数；Kafka lag、磁盘、JVM
和 ES 节点指标仍需在真实监控栈中补充抓取与看板。

K3s 的 `45-backend-availability.yaml` 允许 `monitoring` 命名空间访问后端端口，
但 NetworkPolicy、metrics-server、Prometheus ServiceMonitor 和 Alertmanager 规则
都必须在目标集群中单独验收，仓库没有把“可抓取”冒充为已经监控。

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

告警规则位于 `prometheus-rules.yaml`，覆盖接入拒绝、Bulk 失败、查询延迟、Trace 完整率和
Tempo 降级、FinOps 审计投影失败和 DLT 积压。规则使用固定路由/状态标签，不携带项目、用户、
请求或 Trace ID。

## P0 本地联调栈

`docker-compose.p0.yml` 提供 Tempo、Loki、Collector、Prometheus 和 Grafana 的可重复联调
拓扑，配置文件使用本目录中的固定版本。启动后执行 `./smoke-test.sh` 验证健康端点，再把后端
的 `OTEL_ENABLED=true`、`OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces` 和
`TEMPO_QUERY_ENABLED=true` 指向该栈。生产环境不得直接使用本地文件存储，必须切换到有认证、
Snapshot 和多副本的受管观测组件。
