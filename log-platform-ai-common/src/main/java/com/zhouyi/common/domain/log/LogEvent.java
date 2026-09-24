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
        @JsonProperty("@timestamp") Instant timestamp,
        @JsonProperty("event.ingested") Instant eventIngested,
        @JsonProperty("project.id") String projectId,
        @JsonProperty("deployment.environment") String deploymentEnvironment,
        @JsonProperty("service.name") String serviceName,
        @JsonProperty("service.instance.id") String serviceInstanceId,
        @JsonProperty("trace.id") String traceId,
        @JsonProperty("span.id") String spanId,
        @JsonProperty("log.level") String logLevel,
        @JsonProperty("log.logger") String logLogger,
        @JsonProperty("code.namespace") String codeNamespace,
        @JsonProperty("code.function") String codeFunction,
        String message,
        @JsonProperty("error.type") String errorType,
        @JsonProperty("error.stack_trace") String errorStackTrace,
        Map<String, Object> http,
        @JsonProperty("user.id_hash") String userIdHash,
        Map<String, Object> attributes) {
}
