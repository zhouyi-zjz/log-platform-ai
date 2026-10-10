package com.zhouyi.admin.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** HTTP 路由模板低基数约束测试。 */
class HttpMetricsFilterTest {

    /** 动态文档、Trace 和导出 ID 不得进入指标标签。 */
    @Test
    void normalizesDynamicRoutes() {
        assertThat(HttpMetricsFilter.routeTemplate("/monitor/api/logs/abc123"))
                .isEqualTo("/monitor/api/logs/{id}");
        assertThat(HttpMetricsFilter.routeTemplate("/monitor/api/traces/4bf92f3577b34da6a3ce929d0e0e4736"))
                .isEqualTo("/monitor/api/traces/{traceId}");
        assertThat(HttpMetricsFilter.routeTemplate("/monitor/api/logs/exports/job-1/download"))
                .isEqualTo("/monitor/api/logs/exports");
    }
}
