package com.zhouyi.admin.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * HTTP RED 指标过滤器。
 *
 * <p>路径先归一化为有限的路由模板，再写入 Micrometer 标签；禁止把文档 ID、项目 ID、
 * 用户名或请求 ID写入 Prometheus，避免高基数导致监控系统失控。</p>
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class HttpMetricsFilter extends OncePerRequestFilter {

    private final MeterRegistry registry;

    /** 创建 HTTP 指标过滤器。 */
    public HttpMetricsFilter(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 记录请求总量、状态分类和耗时。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            String route = routeTemplate(request.getRequestURI());
            String statusClass = Integer.toString(response.getStatus() / 100) + "xx";
            registry.counter("log_platform_http_requests_total", "route", route,
                    "method", request.getMethod(), "status_class", statusClass).increment();
            Timer.builder("log_platform_http_request_duration")
                    .description("HTTP 入口请求耗时")
                    .tag("route", route)
                    .tag("method", request.getMethod())
                    .tag("status_class", statusClass)
                    .register(registry)
                    .record(Math.max(0, System.nanoTime() - started), TimeUnit.NANOSECONDS);
        }
    }

    /** 将动态路径归一化为稳定的低基数路由模板。 */
    static String routeTemplate(String path) {
        if (path == null || path.isBlank()) {
            return "unknown";
        }
        if (path.equals("/monitor/api/logs/ingest")) return "/monitor/api/logs/ingest";
        if (path.equals("/monitor/api/logs/search")) return "/monitor/api/logs/search";
        if (path.equals("/monitor/api/logs/aggregations")) return "/monitor/api/logs/aggregations";
        if (path.startsWith("/monitor/api/traces/")) return "/monitor/api/traces/{traceId}";
        if (path.matches("/monitor/api/logs/[^/]+/context")) return "/monitor/api/logs/{id}/context";
        if (path.matches("/monitor/api/logs/[^/]+")) return "/monitor/api/logs/{id}";
        if (path.startsWith("/monitor/api/logs/exports")) return "/monitor/api/logs/exports";
        if (path.startsWith("/monitor/api/admin/")) return "/monitor/api/admin/*";
        if (path.startsWith("/monitor/api/auth/")) return "/monitor/api/auth/*";
        if (path.startsWith("/actuator/")) return "/actuator/*";
        return "/other";
    }
}
