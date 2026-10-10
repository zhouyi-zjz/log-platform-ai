package com.zhouyi.common.domain.telemetry;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * W3C Trace Context 的最小实现。
 *
 * <p>该类只处理传播头的格式和合法性，不创建 Span，也不承担认证授权。它可以复用于
 * HTTP、Kafka、RocketMQ 或 Temporal 的 Header 适配器，避免各边界实现不同的校验规则。</p>
 */
public record W3cTraceContext(String traceId, String spanId, String traceFlags) {

    private static final Pattern TRACEPARENT = Pattern.compile(
            "^00-([0-9a-fA-F]{32})-([0-9a-fA-F]{16})-([0-9a-fA-F]{2})$");

    /** 从标准 traceparent 头解析上下文；非法或全零 ID 返回空值。 */
    public static W3cTraceContext parse(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return null;
        }
        Matcher matcher = TRACEPARENT.matcher(traceparent.trim());
        if (!matcher.matches()) {
            return null;
        }
        String traceId = matcher.group(1).toLowerCase(Locale.ROOT);
        String spanId = matcher.group(2).toLowerCase(Locale.ROOT);
        String flags = matcher.group(3).toLowerCase(Locale.ROOT);
        if (isAllZero(traceId) || isAllZero(spanId)) {
            return null;
        }
        return new W3cTraceContext(traceId, spanId, flags);
    }

    /** 将上下文序列化为可注入下游消息的 traceparent 头。 */
    public String traceparent() {
        return "00-" + traceId + "-" + spanId + "-" + (traceFlags == null ? "01" : traceFlags);
    }

    /** 判断上下文是否包含有效的 32 位 Trace ID 和 16 位 Span ID。 */
    public boolean isValid() {
        return traceId != null && traceId.matches("[0-9a-f]{32}")
                && spanId != null && spanId.matches("[0-9a-f]{16}")
                && !isAllZero(traceId) && !isAllZero(spanId);
    }

    /** 判断字符串是否全部为零。 */
    private static boolean isAllZero(String value) {
        return value != null && value.chars().allMatch(character -> character == '0');
    }
}
