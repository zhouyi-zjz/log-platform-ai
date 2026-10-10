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
        /** 查询起始时间，ISO-8601。 */
        String from,
        /** 查询结束时间，ISO-8601，采用左闭右开区间。 */
        String to,
        /** 项目 ID。 */
        @JsonProperty("project.id") String projectId,
        /** 部署环境。 */
        @JsonProperty("deployment.environment") String deploymentEnvironment,
        /** 服务名称。 */
        @JsonProperty("service.name") String serviceName,
        /** 服务实例 ID。 */
        @JsonProperty("service.instance.id") String serviceInstanceId,
        /** Trace ID。 */
        @JsonProperty("trace.id") String traceId,
        /** Span ID。 */
        @JsonProperty("span.id") String spanId,
        /** 日志级别。 */
        @JsonProperty("log.level") String logLevel,
        /** 异常类型。 */
        @JsonProperty("error.type") String errorType,
        /** 对日志正文和异常堆栈执行的简单关键字查询。 */
        String keyword,
        /** 受控的自定义属性精确过滤。 */
        Map<String, Object> attributes,
        /** 单页条数。 */
        Integer size,
        /** 上一页响应返回的 search_after 游标。 */
        List<Object> searchAfter) {
}
