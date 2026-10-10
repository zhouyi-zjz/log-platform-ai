package com.zhouyi.admin.service;

import com.zhouyi.common.domain.log.LogEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Trace 完整率聚合和缺失字段巡检测试。 */
class TraceCompletenessServiceTest {

    /** 完整和不完整事件应产生可解释的聚合结果。 */
    @Test
    void reportsMissingCorrelationFieldsWithoutExposingEventData() {
        TraceCompletenessService service = new TraceCompletenessService(new SimpleMeterRegistry());
        service.inspect(event("trace", "span", "request"));
        service.inspect(event("", "span", ""));

        var snapshot = service.snapshot();

        assertThat(snapshot.inspected()).isEqualTo(2);
        assertThat(snapshot.complete()).isEqualTo(1);
        assertThat(snapshot.completenessRate()).isEqualTo(0.5D);
        assertThat(snapshot.missingByField()).containsEntry("trace.id", 1L)
                .containsEntry("request.id", 1L);
    }

    /** 创建最小测试事件。 */
    private static LogEvent event(String trace, String span, String request) {
        return new LogEvent(Instant.now(), Instant.now(), "event", "project", "prod", "service",
                "instance", trace, span, request, "INFO", "logger", "namespace", "function",
                "message", null, null, null, null, null);
    }
}
