package com.zhouyi.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouyi.admin.config.LogIngestProperties;
import com.zhouyi.admin.config.LogQueryProperties;
import com.zhouyi.admin.domain.log.LogAggregationRequest;
import com.zhouyi.admin.domain.log.LogContextRequest;
import com.zhouyi.admin.domain.log.LogSearchRequest;
import com.zhouyi.admin.domain.log.ProjectAccessScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LogQueryServiceTest {

    private MockRestServiceServer server;
    private LogQueryService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://elasticsearch:9200");
        server = MockRestServiceServer.bindTo(builder).build();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        LogIngestProperties ingestProperties = new LogIngestProperties();
        LogQueryProperties queryProperties = new LogQueryProperties();
        service = new LogQueryService(builder.build(), objectMapper, ingestProperties, queryProperties,
                new LogQueryWindowService(queryProperties));
    }

    @Test
    void restrictedUserQueryInjectsProjectTermsAndSortableEventId() {
        server.expect(once(), requestTo("http://elasticsearch:9200/logs-platform-default/_search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"project.id\":[\"billing\"]")))
                .andExpect(content().string(containsString("\"event.id\":{\"order\":\"desc\"}")))
                .andRespond(withSuccess("""
                        {"hits":{"total":{"value":0},"hits":[]}}
                        """, MediaType.APPLICATION_JSON));

        service.search(null, ProjectAccessScope.restricted(Set.of("billing")));

        server.verify();
    }

    @Test
    void restrictedUserCannotRequestAnotherProject() {
        LogSearchRequest request = new LogSearchRequest(
                null, null, "orders", null, null, null, null, null,
                null, null, null, null, 50, null);

        assertThatThrownBy(() -> service.search(request, ProjectAccessScope.restricted(Set.of("billing"))))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("无权访问项目: orders");
    }

    @Test
    void detailForRestrictedUserInjectsProjectScope() {
        server.expect(once(), requestTo("http://elasticsearch:9200/logs-platform-default/_search"))
                .andExpect(content().string(containsString("\"project.id\":[\"billing\"]")))
                .andExpect(content().string(containsString("\"ids\":{\"values\":[\"doc-1\"]}")))
                .andRespond(withSuccess("""
                        {"hits":{"hits":[{"_id":"doc-1","_index":"logs-platform-default",
                        "_source":{"project.id":"billing"},"sort":["2026-09-30T00:00:00Z","evt-1"]}]}}
                        """, MediaType.APPLICATION_JSON));

        service.detail("doc-1", ProjectAccessScope.restricted(Set.of("billing")));

        server.verify();
    }

    @Test
    void contextCannotLeaveTargetLogProject() {
        String timestamp = Instant.now().toString();
        server.expect(once(), requestTo("http://elasticsearch:9200/logs-platform-default/_search"))
                .andExpect(content().string(containsString("\"project.id\":[\"billing\"]")))
                .andRespond(withSuccess("""
                        {"hits":{"hits":[{"_id":"doc-1","_index":"logs-platform-default",
                        "_source":{"@timestamp":"%s","project.id":"billing","service.name":"api"},
                        "sort":["%s","evt-1"]}]}}
                        """.formatted(timestamp, timestamp), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://elasticsearch:9200/logs-platform-default/_search"))
                .andExpect(content().string(containsString("\"term\":{\"project.id\":\"billing\"}")))
                .andRespond(withSuccess("""
                        {"hits":{"total":{"value":0},"hits":[]}}
                        """, MediaType.APPLICATION_JSON));

        service.context("doc-1", new LogContextRequest(null, null, null, 10, 10),
                ProjectAccessScope.restricted(Set.of("billing")));

        server.verify();
    }

    @Test
    void aggregationForRestrictedUserInjectsProjectScope() {
        server.expect(once(), requestTo("http://elasticsearch:9200/logs-platform-default/_search"))
                .andExpect(content().string(containsString("\"project.id\":[\"billing\"]")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service.aggregate(new LogAggregationRequest(
                        new LogSearchRequest(null, null, null, null, null, null, null,
                                null, null, null, null, null, 10, null),
                        List.of("service.name"), 10),
                ProjectAccessScope.restricted(Set.of("billing")));

        server.verify();
    }

    @Test
    void timedOutElasticsearchResponseIsNotReportedAsSuccessful() {
        server.expect(once(), requestTo("http://elasticsearch:9200/logs-platform-default/_search"))
                .andRespond(withSuccess("{\"timed_out\":true}", MediaType.APPLICATION_JSON));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.search(null, ProjectAccessScope.restricted(Set.of("billing"))))
                .isInstanceOf(LogQueryService.QueryTimeoutException.class)
                .hasMessageContaining("查询超时");
        server.verify();
    }

    @Test
    void searchResponseMasksSensitiveFields() {
        server.expect(once(), requestTo("http://elasticsearch:9200/logs-platform-default/_search"))
                .andRespond(withSuccess("""
                        {"hits":{"total":{"value":1},"hits":[
                          {"_id":"doc-1","_index":"logs-platform-default",
                           "_source":{"project.id":"billing","token":"secret"},
                           "sort":["2026-09-30T00:00:00Z","evt-1"]}]}}
                        """, MediaType.APPLICATION_JSON));

        var response = service.search(null, ProjectAccessScope.restricted(Set.of("billing")));

        org.assertj.core.api.Assertions.assertThat(response.items().get(0).source().path("token").asText())
                .isEqualTo("[REDACTED]");
        server.verify();
    }
}
