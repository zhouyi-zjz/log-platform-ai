package com.zhouyi.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouyi.admin.config.LogIngestProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElasticsearchLogWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ElasticsearchLogWriter writer = new ElasticsearchLogWriter(
            null, objectMapper, new LogIngestProperties());
    private final LogEventNormalizer normalizer = new LogEventNormalizer(objectMapper);

    @Test
    void bulkCreateUsesProjectScopedStableDocumentId() throws Exception {
        var event = normalizer.normalize("""
                {"event.id":"evt-42","project.id":"billing","message":"created"}
                """);
        var sameEventIdInAnotherProject = normalizer.normalize("""
                {"event.id":"evt-42","project.id":"orders","message":"created"}
                """);

        String body = writer.buildBulkBody(List.of(event));

        assertThat(body).startsWith("{\"create\":{\"_id\":\""
                + ElasticsearchLogWriter.documentId(event) + "\"}}\n");
        assertThat(body).contains("\"event.id\":\"evt-42\"");
        assertThat(ElasticsearchLogWriter.documentId(event))
                .isNotEqualTo(ElasticsearchLogWriter.documentId(sameEventIdInAnotherProject));
    }

    @Test
    void duplicateConflictIsTreatedAsIdempotentSuccess() {
        assertThatCode(() -> writer.validateBulkResponse("""
                {"errors":true,"items":[{"create":{"_id":"evt-42","status":409}}]}
                """))
                .doesNotThrowAnyException();
    }

    @Test
    void nonConflictBulkFailureIsRetriedByCaller() {
        assertThatThrownBy(() -> writer.validateBulkResponse("""
                {"errors":true,"items":[{"create":{"_id":"evt-42","status":429}}]}
                """))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("evt-42:429");
    }

    @Test
    void emptyOrMalformedBulkResponseIsRejected() {
        assertThatThrownBy(() -> writer.validateBulkResponse(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("空响应");
        assertThatThrownBy(() -> writer.validateBulkResponse("{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少 errors 字段");
        assertThatThrownBy(() -> writer.validateBulkResponse("{\"errors\":true,\"items\":[]}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少 items");
    }
}
