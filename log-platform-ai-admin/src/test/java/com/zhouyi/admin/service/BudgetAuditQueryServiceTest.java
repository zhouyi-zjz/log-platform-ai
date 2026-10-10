package com.zhouyi.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouyi.admin.config.FinopsIntegrationProperties;
import com.zhouyi.admin.config.LogIngestProperties;
import com.zhouyi.admin.config.LogQueryProperties;
import com.zhouyi.admin.domain.log.BudgetAuditSearchRequest;
import com.zhouyi.admin.domain.log.ProjectAccessScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** FinOps 预算审计查询的 ACL、游标和脱敏回归测试。 */
class BudgetAuditQueryServiceTest {

    private MockRestServiceServer server;
    private BudgetAuditQueryService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://elasticsearch:9200");
        server = MockRestServiceServer.bindTo(builder).build();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        LogQueryProperties queryProperties = new LogQueryProperties();
        FinopsIntegrationProperties finopsProperties = new FinopsIntegrationProperties();
        service = new BudgetAuditQueryService(builder.build(), objectMapper, finopsProperties,
                queryProperties, new LogQueryWindowService(queryProperties), new LogFieldRedactor());
    }

    @Test
    void restrictedSearchInjectsProjectScopeAndReturnsStableCursor() {
        server.expect(once(), requestTo("http://elasticsearch:9200/finops-budget-audit-v1/_search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"project.id\":[\"billing\"]")))
                .andExpect(content().string(containsString("\"search_after\":[\"2026-10-09T00:00:00Z\",\"evt-0\"]")))
                .andRespond(withSuccess("""
                        {"hits":{"total":{"value":1},"hits":[
                          {"_id":"doc-1","_source":{"project.id":"billing","token":"secret"},
                           "sort":["2026-10-09T00:01:00Z","evt-1"]}]}}
                        """, MediaType.APPLICATION_JSON));

        var response = service.search(new BudgetAuditSearchRequest(null, null, null, "ALLOW",
                        null, null, 20, List.of("2026-10-09T00:00:00Z", "evt-0")),
                ProjectAccessScope.restricted(Set.of("billing")));

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.nextSearchAfter()).containsExactly("2026-10-09T00:01:00Z", "evt-1");
        assertThat(response.items().get(0).path("token").asText()).isEqualTo("[REDACTED]");
        server.verify();
    }

    @Test
    void restrictedSearchCannotRequestAnotherProject() {
        assertThatThrownBy(() -> service.search(
                new BudgetAuditSearchRequest(null, null, "orders", null, null, null, 20, null),
                ProjectAccessScope.restricted(Set.of("billing"))))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("无权访问该项目");
    }
}
