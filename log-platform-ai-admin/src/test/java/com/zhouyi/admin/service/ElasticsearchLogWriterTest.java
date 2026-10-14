package com.zhouyi.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouyi.admin.config.LogIngestProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
    void bulkRequestWritesChineseAsUtf8Bytes() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://elasticsearch:9200");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LogIngestProperties properties = new LogIngestProperties();
        ElasticsearchLogWriter httpWriter = new ElasticsearchLogWriter(
                builder.build(), objectMapper, properties);
        var event = normalizer.normalize("""
                {"event.id":"evt-utf8","project.id":"area","message":"全局异常处理 <未知异常> ======> 请求地址 </area_graph/list>"}
                """);
        byte[] expectedBody = httpWriter.buildBulkBody(List.of(event))
                .getBytes(StandardCharsets.UTF_8);

        server.expect(once(), requestTo("http://elasticsearch:9200/"
                        + properties.getElasticsearchIndex() + "/_bulk"))
                .andExpect(content().contentType(MediaType.parseMediaType("application/x-ndjson")))
                .andExpect(content().bytes(expectedBody))
                .andRespond(withSuccess("{\"errors\":false,\"items\":[]}", MediaType.APPLICATION_JSON));

        httpWriter.writeBulk(List.of(event));

        server.verify();
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
