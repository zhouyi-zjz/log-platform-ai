package com.zhouyi.admin.config;

import com.zhouyi.common.domain.telemetry.TelemetryContext;

/**
 * 保存当前 HTTP 线程的可信遥测上下文。
 *
 * <p>该 Holder 只用于入口适配和日志关联，业务代码不应通过它获取授权信息；
 * 请求结束时必须清理，避免容器线程复用导致上下文串线。</p>
 */
public final class TelemetryContextHolder {

    private static final ThreadLocal<TelemetryContext> CURRENT = new ThreadLocal<>();

    private TelemetryContextHolder() {
    }

    /** 设置当前请求上下文。 */
    public static void set(TelemetryContext context) {
        CURRENT.set(context);
    }

    /** 获取当前请求上下文；非 HTTP 线程返回空值。 */
    public static TelemetryContext current() {
        return CURRENT.get();
    }

    /** 清理当前线程上下文。 */
    public static void clear() {
        CURRENT.remove();
    }
}
