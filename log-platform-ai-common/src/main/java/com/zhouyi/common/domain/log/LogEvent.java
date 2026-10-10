package com.zhouyi.common.domain.log;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * <p>字段命名参考 OpenTelemetry/ECS。Java 字段采用驼峰命名，序列化时
 * 通过 {@link JsonProperty} 保留平台约定的点号字段名。http 和 attributes
 * 故意保留为 Map，方便不同项目携带各自的 HTTP 细节和扩展属性。</p>
 *
 * @author zjz
 * @version 1.0
 * @description: 日志平台内部统一使用的日志事件模型
 * @date 2026/9/12 17:32
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogEvent(
        /** 业务事件发生时间。 */
        @JsonProperty("@timestamp") Instant timestamp,
        /** 平台接收时间，禁止信任采集端传入值。 */
        @JsonProperty("event.ingested") Instant eventIngested,
        /** 事件在项目内的幂等标识。 */
        @JsonProperty("event.id") String eventId,
        /** 项目租户标识。 */
        @JsonProperty("project.id") String projectId,
        /** 部署环境。 */
        @JsonProperty("deployment.environment") String deploymentEnvironment,
        /** 服务名称。 */
        @JsonProperty("service.name") String serviceName,
        /** 服务实例标识。 */
        @JsonProperty("service.instance.id") String serviceInstanceId,
        /** W3C Trace ID。 */
        @JsonProperty("trace.id") String traceId,
        /** W3C Span ID。 */
        @JsonProperty("span.id") String spanId,
        /** HTTP/MQ 请求关联标识。 */
        @JsonProperty("request.id") String requestId,
        /** 日志级别。 */
        @JsonProperty("log.level") String logLevel,
        /** 日志器名称。 */
        @JsonProperty("log.logger") String logLogger,
        /** 代码命名空间。 */
        @JsonProperty("code.namespace") String codeNamespace,
        /** 代码函数名。 */
        @JsonProperty("code.function") String codeFunction,
        /** 日志正文。 */
        String message,
        /** 异常类型。 */
        @JsonProperty("error.type") String errorType,
        /** 异常堆栈。 */
        @JsonProperty("error.stack_trace") String errorStackTrace,
        /** HTTP 结构化扩展字段。 */
        Map<String, Object> http,
        /** 脱敏后的用户标识摘要。 */
        @JsonProperty("user.id_hash") String userIdHash,
        /** 项目自定义扩展字段。 */
        Map<String, Object> attributes) {
}
