package com.zhouyi.admin.domain.log;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 日志检索请求。只接受平台定义的过滤字段，不接受任意 Elasticsearch DSL。
 *
 * <p>时间使用 ISO-8601，例如 {@code 2026-09-12T00:00:00+08:00}。
 * {@code searchAfter} 必须原样使用上一页响应中的游标。</p>
 */
public record LogSearchRequest(
        String from,
        String to,
        @JsonProperty("project.id") String projectId,
        @JsonProperty("deployment.environment") String deploymentEnvironment,
        @JsonProperty("service.name") String serviceName,
        @JsonProperty("service.instance.id") String serviceInstanceId,
        @JsonProperty("trace.id") String traceId,
        @JsonProperty("span.id") String spanId,
        @JsonProperty("log.level") String logLevel,
        @JsonProperty("error.type") String errorType,
        String keyword,
        Map<String, Object> attributes,
        Integer size,
        List<Object> searchAfter) {
}
