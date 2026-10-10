package com.zhouyi.admin.config;

import com.zhouyi.common.domain.telemetry.TelemetryContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.opentelemetry.api.trace.Span;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP 入口的 W3C Trace Context 兼容层。
 *
 * <p>正式部署可由 OpenTelemetry Java Agent 创建真实 Span；该过滤器负责在没有 Agent
 * 或 Collector 暂时不可用时仍提供稳定的 request/trace 关联头和 MDC，并且不会阻塞请求。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TelemetryContextFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String TRACE_ID_HEADER = "X-Trace-ID";
    private static final String TRACEPARENT_HEADER = "traceparent";
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._~-]{1,128}");
    private static final Pattern TRACEPARENT = Pattern.compile(
            "^[\"']?([0-9a-fA-F]{2})-([0-9a-fA-F]{32})-([0-9a-fA-F]{16})-([0-9a-fA-F]{2})[\"']?$");
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 建立上下文、绑定 MDC 并在响应中返回关联 ID。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = validRequestId(request.getHeader(REQUEST_ID_HEADER));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        TraceIds traceIds = currentSpanIds();
        if (traceIds == null) {
            traceIds = parseTraceparent(request.getHeader(TRACEPARENT_HEADER));
            if (traceIds == null) {
                traceIds = new TraceIds(randomHex(16), randomHex(8));
            } else {
                traceIds = new TraceIds(traceIds.traceId(), randomHex(8));
            }
        }
        TelemetryContext context = TelemetryContext.ofRequest(requestId, traceIds.traceId(), traceIds.spanId());
        TelemetryContextHolder.set(context);
        MDC.put("request.id", context.effectiveRequestId());
        MDC.put("trace.id", traceIds.traceId());
        MDC.put("span.id", traceIds.spanId());
        response.setHeader(REQUEST_ID_HEADER, context.effectiveRequestId());
        response.setHeader(TRACE_ID_HEADER, traceIds.traceId());
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("request.id");
            MDC.remove("trace.id");
            MDC.remove("span.id");
            TelemetryContextHolder.clear();
        }
    }

    /** 校验并限制客户端请求 ID，非法值由平台重新生成。 */
    private static String validRequestId(String value) {
        if (value == null || !REQUEST_ID.matcher(value).matches()) {
            return null;
        }
        return value;
    }

    /** 提取 W3C Trace ID；不接受全零 Trace/Span，避免伪造无效链路。 */
    private static TraceIds parseTraceparent(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher matcher = TRACEPARENT.matcher(value.trim());
        if (!matcher.matches()) {
            return null;
        }
        String traceId = matcher.group(2).toLowerCase(Locale.ROOT);
        String spanId = matcher.group(3).toLowerCase(Locale.ROOT);
        if (traceId.chars().allMatch(c -> c == '0') || spanId.chars().allMatch(c -> c == '0')) {
            return null;
        }
        return new TraceIds(traceId, spanId);
    }

    /** 优先采用 Java Agent/SDK 当前 Span，避免兼容层与真实 OTel 链路产生两套 ID。 */
    private static TraceIds currentSpanIds() {
        Span span = Span.current();
        if (span == null || !span.getSpanContext().isValid()) {
            return null;
        }
        return new TraceIds(span.getSpanContext().getTraceId(), span.getSpanContext().getSpanId());
    }

    /** 生成十六进制关联 ID。 */
    private static String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        StringBuilder result = new StringBuilder(bytes * 2);
        for (byte item : value) {
            result.append(String.format("%02x", item & 0xff));
        }
        return result.toString();
    }

    /** 解析后的 Trace 标识。 */
    private record TraceIds(String traceId, String spanId) {
    }
}
