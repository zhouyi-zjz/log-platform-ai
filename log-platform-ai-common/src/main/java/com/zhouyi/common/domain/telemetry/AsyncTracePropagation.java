package com.zhouyi.common.domain.telemetry;

import java.util.Map;

/**
 * 与消息 SDK 无关的异步传播端口。
 *
 * <p>RocketMQ、Temporal、HTTP 回调等适配器可以把自身 Header/Metadata 映射到这个端口，
 * 从而复用统一的 W3C 校验和 request.id 规则。它不依赖具体消息中间件，不会把 SDK 类型
 * 泄漏到领域模型。</p>
 */
public final class AsyncTracePropagation {

    /** 标准 W3C Trace Context 键名。 */
    public static final String TRACEPARENT = "traceparent";
    /** 平台请求关联键名。 */
    public static final String REQUEST_ID = "x-request-id";

    private AsyncTracePropagation() {
    }

    /** 向可变 Header Map 注入上下文；上下文无效时保持 fail-open。 */
    public static void inject(Map<String, String> headers, TelemetryContext context) {
        if (headers == null || context == null || context.traceId() == null || context.spanId() == null) {
            return;
        }
        W3cTraceContext trace = new W3cTraceContext(context.traceId(), context.spanId(), "01");
        if (!trace.isValid()) {
            return;
        }
        headers.put(TRACEPARENT, trace.traceparent());
        if (context.requestId() != null && !context.requestId().isBlank()) {
            headers.put(REQUEST_ID, context.requestId());
        }
    }

    /** 从只读 Header Map 提取上下文；非法数据按缺失处理。 */
    public static TelemetryContext extract(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
        W3cTraceContext trace = W3cTraceContext.parse(headers.get(TRACEPARENT));
        if (trace == null) {
            return null;
        }
        return TelemetryContext.ofRequest(headers.get(REQUEST_ID), trace.traceId(), trace.spanId());
    }
}
