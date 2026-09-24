package com.zhouyi.admin.domain.log;

/**
 * 上下文查询参数。以目标日志时间为中心读取前后时间窗口，窗口会再次经过用户权限裁剪。
 * before/after 使用秒，最大值由服务端限制，避免一次上下文请求扫描过大的时间范围。
 */
public record LogContextRequest(
        String timestamp,
        String traceId,
        String serviceName,
        Integer beforeSeconds,
        Integer afterSeconds) {
}
