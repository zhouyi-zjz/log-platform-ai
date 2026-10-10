package com.zhouyi.admin.domain.log;

/**
 * 上下文查询参数。以目标日志时间为中心读取前后时间窗口，窗口会再次经过用户权限裁剪。
 * before/after 使用秒，最大值由服务端限制，避免一次上下文请求扫描过大的时间范围。
 */
public record LogContextRequest(
        /** 目标日志时间，可为空并从服务端详情读取。 */
        String timestamp,
        /** 可选 Trace 过滤。 */
        String traceId,
        /** 可选服务过滤。 */
        String serviceName,
        /** 上下文向前扩展秒数，最大 300 秒。 */
        Integer beforeSeconds,
        /** 上下文向后扩展秒数，最大 300 秒。 */
        Integer afterSeconds) {
}
