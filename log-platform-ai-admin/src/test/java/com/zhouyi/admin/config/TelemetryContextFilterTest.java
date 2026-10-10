package com.zhouyi.admin.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** HTTP 入口遥测上下文的契约测试。 */
class TelemetryContextFilterTest {

    private final TelemetryContextFilter filter = new TelemetryContextFilter();

    /** 每个测试结束后清理线程上下文，模拟 Servlet 线程归还容器。 */
    @AfterEach
    void clearContext() {
        TelemetryContextHolder.clear();
    }

    /** 合法 W3C Trace ID 应被继承，Span ID 则应为当前入口重新生成。 */
    @Test
    void inheritsTraceIdAndCreatesResponseCorrelationHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/monitor/api/logs/ingest");
        request.addHeader("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        request.addHeader("X-Request-ID", "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            assertThat(TelemetryContextHolder.current().traceId())
                    .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
            assertThat(TelemetryContextHolder.current().requestId()).isEqualTo("request-123");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Request-ID")).isEqualTo("request-123");
        assertThat(response.getHeader("X-Trace-ID"))
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(TelemetryContextHolder.current()).isNull();
    }

    /** 非法或过长请求 ID 不能被原样写入日志上下文。 */
    @Test
    void replacesInvalidRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/monitor/api/logs/search");
        request.addHeader("X-Request-ID", "bad value with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getHeader("X-Request-ID")).isNotEqualTo("bad value with spaces");
        assertThat(response.getHeader("X-Request-ID")).isNotBlank();
    }
}
