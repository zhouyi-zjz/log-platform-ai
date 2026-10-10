package com.zhouyi.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhouyi.admin.config.FinopsIntegrationProperties;
import com.zhouyi.admin.config.LogIngestProperties;
import com.zhouyi.admin.config.LogQueryProperties;
import com.zhouyi.admin.domain.log.BudgetAuditSearchRequest;
import com.zhouyi.admin.domain.log.BudgetAuditSearchResponse;
import com.zhouyi.admin.domain.log.ProjectAccessScope;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/** 预算审计只读检索服务，强制复用项目 ACL 和时间窗口。 */
@Service
public class BudgetAuditQueryService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final FinopsIntegrationProperties finopsProperties;
    private final LogQueryProperties queryProperties;
    private final LogQueryWindowService windowService;
    private final LogFieldRedactor redactor;

    /** 创建预算审计查询服务。 */
    public BudgetAuditQueryService(RestClient restClient, ObjectMapper objectMapper,
                                   FinopsIntegrationProperties finopsProperties,
                                   LogQueryProperties queryProperties,
                                   LogQueryWindowService windowService,
                                   LogFieldRedactor redactor) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.finopsProperties = finopsProperties;
        this.queryProperties = queryProperties;
        this.windowService = windowService;
        this.redactor = redactor;
    }

    /** 查询预算审计投影，不支持写入、修改或任意 DSL。 */
    public BudgetAuditSearchResponse search(BudgetAuditSearchRequest request, ProjectAccessScope scope) {
        requireScope(scope);
        BudgetAuditSearchRequest safe = request == null
                ? new BudgetAuditSearchRequest(null, null, null, null, null, null, null, null) : request;
        LogQueryWindowService.QueryWindow window = windowService.resolve(safe.from(), safe.to(), scope.admin());
        int size = Math.max(1, Math.min(safe.size() == null ? 100 : safe.size(), queryProperties.getMaxPageSize()));
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", size);
        body.put("track_total_hits", 10000);
        body.put("timeout", queryProperties.getTimeout());
        ArrayNode sort = body.putArray("sort");
        sort.addObject().putObject("@timestamp").put("order", "desc");
        sort.addObject().putObject("event.id").put("order", "desc");
        ArrayNode filters = body.putObject("query").putObject("bool").putArray("filter");
        ObjectNode range = filters.addObject().putObject("range").putObject("@timestamp");
        range.put("gte", window.from().toString());
        range.put("lt", window.to().toString());
        addTerm(filters, "event.channel", "BUDGET_AUDIT");
        addProjectFilter(filters, safe.projectId(), scope);
        addTerm(filters, "decision", safe.decision());
        addTerm(filters, "reservation.id", safe.reservationId());
        addTerm(filters, "run.id", safe.runId());
        if (safe.searchAfter() != null && !safe.searchAfter().isEmpty()) {
            if (safe.searchAfter().size() != 2) {
                throw new IllegalArgumentException("searchAfter 必须包含 @timestamp 和 event.id 两个值");
            }
            body.set("search_after", objectMapper.valueToTree(safe.searchAfter()));
        }
        JsonNode response = restClient.post().uri("/{index}/_search", finopsProperties.getBudgetAuditIndex())
                .body(body).retrieve().body(JsonNode.class);
        return toResponse(response);
    }

    /** 将 ES 响应转换为脱敏后的稳定 API 响应。 */
    private BudgetAuditSearchResponse toResponse(JsonNode response) {
        long total = response == null ? 0 : response.path("hits").path("total").path("value").asLong(0);
        List<JsonNode> items = new ArrayList<>();
        List<Object> cursor = List.of();
        JsonNode hits = response == null ? null : response.path("hits").path("hits");
        if (hits != null && hits.isArray()) {
            for (JsonNode hit : hits) {
                items.add(redactor.redact(hit.path("_source")));
                if (hit.has("sort")) {
                    List<Object> nextCursor = new ArrayList<>();
                    hit.path("sort").forEach(value -> nextCursor.add(objectMapper.convertValue(value, Object.class)));
                    cursor = nextCursor;
                }
            }
        }
        return new BudgetAuditSearchResponse(items, total, cursor);
    }

    /** 添加精确过滤。 */
    private static void addTerm(ArrayNode filters, String field, String value) {
        if (value != null && !value.isBlank()) {
            filters.addObject().putObject("term").put(field, value);
        }
    }

    /** 添加管理员或普通用户的项目范围过滤。 */
    private void addProjectFilter(ArrayNode filters, String requestedProject, ProjectAccessScope scope) {
        if (requestedProject != null && !requestedProject.isBlank()) {
            if (!scope.admin() && !scope.projectIds().contains(requestedProject)) {
                throw new org.springframework.security.access.AccessDeniedException("无权访问该项目");
            }
            addTerm(filters, "project.id", requestedProject);
            return;
        }
        if (!scope.admin()) {
            ObjectNode terms = filters.addObject().putObject("terms");
            terms.set("project.id", objectMapper.valueToTree(scope.projectIds()));
        }
    }

    /** 拒绝没有服务端项目范围的调用。 */
    private static void requireScope(ProjectAccessScope scope) {
        if (scope == null || (!scope.admin() && scope.projectIds().isEmpty())) {
            throw new org.springframework.security.access.AccessDeniedException("项目权限不能为空");
        }
    }
}
