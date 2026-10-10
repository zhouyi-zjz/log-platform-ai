package com.zhouyi.common.domain.telemetry;

import java.util.UUID;

/**
 * 跨日志、Trace 和外部事件的可信关联上下文。
 *
 * <p>该对象只保存关联元数据，不保存 Prompt、Completion、工具参数、凭据或正文。
 * 租户和项目必须由服务端认证/映射得到，不能使用模型输出或普通请求字段覆盖。</p>
 */
public record TelemetryContext(
        /** 认证上下文提供的租户标识。 */
        String tenantId,
        /** 日志平台项目标识。 */
        String projectId,
        /** 服务名称。 */
        String serviceName,
        /** 部署环境。 */
        String deploymentEnvironment,
        /** 请求关联标识。 */
        String requestId,
        /** W3C Trace ID。 */
        String traceId,
        /** 当前 Span ID。 */
        String spanId,
        /** Agent/业务运行标识。 */
        String runId) {

    /** 创建一个带平台请求 ID 的最小上下文。 */
    public static TelemetryContext ofRequest(String requestId, String traceId, String spanId) {
        return new TelemetryContext(null, null, null, null,
                normalize(requestId, UUID.randomUUID().toString()),
                normalize(traceId, null), normalize(spanId, null), null);
    }

    /** 返回当前上下文中可用于日志关联的请求 ID。 */
    public String effectiveRequestId() {
        return normalize(requestId, UUID.randomUUID().toString());
    }

    /** 对外部输入做空白归一化，避免把空字符串写入遥测后端。 */
    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
