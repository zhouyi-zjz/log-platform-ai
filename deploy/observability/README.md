# OTel（可选）与 SkyWalking 采集参数

## OpenTelemetry Java Agent（可选）

```bash
JAVA_TOOL_OPTIONS="-javaagent:/opt/otel/opentelemetry-javaagent.jar"
OTEL_SERVICE_NAME=billing-api
OTEL_RESOURCE_ATTRIBUTES="project.id=billing,deployment.environment=prod"
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
OTEL_PROPAGATORS=tracecontext,baggage
```

应用日志框架需要把当前 Span 的 trace/span id 写入 JSON 字段 `trace.id`、`span.id`。
Collector 的 OTLP exporter 应指向 SkyWalking OAP 的 OTLP gRPC 接收端（示例见同目录配置）。

## SkyWalking Agent

```bash
JAVA_TOOL_OPTIONS="-javaagent:/opt/skywalking-agent/skywalking-agent.jar"
SW_AGENT_NAME=billing-api
SW_AGENT_COLLECTOR_BACKEND_SERVICES=skywalking-oap:11800
```

SkyWalking Agent 与 OTel Agent 不建议在同一 JVM 同时启用。若确实需要双采集，必须关闭重复插件，
并验证日志中的 `trace.id` 与 SkyWalking/Otel 后端的 Trace ID 映射后再上线。
