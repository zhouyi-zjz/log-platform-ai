package com.zhouyi.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 只覆盖字段归一化的关键约束，Kafka/ES 集成测试留给部署环境的联调测试。 */
class LogEventNormalizerTest {

    private final LogEventNormalizer normalizer = new LogEventNormalizer(new ObjectMapper());

    @Test
    void normalizesCoreFieldsAndBothMapStyles() {
        var event = normalizer.normalize("""
                {
                  "@timestamp":"2026-09-12T09:30:00Z",
                  "project.id":"billing",
                  "service.name":"billing-api",
                  "http.method":"POST",
                  "http.status_code":504,
                  "attributes":{"retry.count":2},
                  "message":"timeout"
                }
                """);

        assertThat(event.projectId()).isEqualTo("billing");
        assertThat(event.timestamp().toString()).isEqualTo("2026-09-12T09:30:00Z");
        assertThat(event.http()).containsEntry("method", "POST").containsEntry("status_code", 504);
        assertThat(event.attributes()).containsEntry("retry.count", 2);
        assertThat(event.eventIngested()).isNotNull();
    }

    @Test
    void rejectsEventWithoutProjectId() {
        assertThatThrownBy(() -> normalizer.normalize("{\"message\":\"missing project\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("project.id 不能为空");
    }
}
