package com.zhouyi.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhouyi.admin.config.LogIngestProperties;
import com.zhouyi.admin.config.LogQueryProperties;
import com.zhouyi.admin.domain.log.LogAggregationRequest;
import com.zhouyi.admin.domain.log.LogContextRequest;
import com.zhouyi.admin.domain.log.LogSearchRequest;
import com.zhouyi.admin.domain.log.LogSearchResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>所有 DSL 都由这里根据白名单字段拼装，调用方不能直接提交任意 Elasticsearch DSL。
 * 这样既能把权限时间范围强制落实到每次请求，也能避免深分页和高开销查询绕过限制。</p>
 *
 * @author zjz
 * @version 1.0
 * @description: 日志检索核心服务
 * @date 2026/9/12
 */
@Service
public class LogQueryService {

    private static final Set<String> AGGREGATION_FIELDS = Set.of(
            "project.id", "deployment.environment", "service.name",
            "service.instance.id", "log.level", "error.type",
            "http.method", "http.status_code", "http.route");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final LogIngestProperties ingestProperties;
    private final LogQueryProperties queryProperties;
    private final LogQueryWindowService windowService;

    public LogQueryService(RestClient elasticsearchRestClient,
                           ObjectMapper objectMapper,
                           LogIngestProperties ingestProperties,
                           LogQueryProperties queryProperties,
                           LogQueryWindowService windowService) {
        this.restClient = elasticsearchRestClient;
        this.objectMapper = objectMapper;
        this.ingestProperties = ingestProperties;
        this.queryProperties = queryProperties;
        this.windowService = windowService;
    }

    public LogSearchResponse search(LogSearchRequest request, boolean admin) {
        LogQueryWindowService.QueryWindow window = windowService.resolve(
                request == null ? null : request.from(), request == null ? null : request.to(), admin);
        int size = normalizeSize(request == null ? null : request.size());
        ObjectNode body = baseSearchBody(window, size);
        body.set("query", buildQuery(request, window));
        if (request != null && request.searchAfter() != null && !request.searchAfter().isEmpty()) {
            validateSearchAfter(request.searchAfter());
            body.set("search_after", objectMapper.valueToTree(request.searchAfter()));
        }
        JsonNode response = executeSearch(body);
        return toSearchResponse(response, window);
    }

    /**
     * 查询单条详情前仍然检查 @timestamp，不能因为知道文档 ID 就绕过普通用户的时间窗口。
     */
    public LogSearchResponse.LogHit detail(String id, boolean admin) {
        if (id == null || id.isBlank() || id.length() > 512) {
            throw new IllegalArgumentException("日志 ID 无效");
        }
        // 不直接调用 GET /{index}/_doc/{id}：日志通常写入 Data Stream 的 backing index，
        // 通过受权限窗口约束的 _search 查详情，避免文档 ID 成为越权旁路。
        LogQueryWindowService.QueryWindow defaultWindow = windowService.resolve(null, null, admin);
        ObjectNode body = baseSearchBody(defaultWindow, 1);
        ObjectNode bool = body.putObject("query").putObject("bool");
        ArrayNode filters = bool.putArray("filter");
        ObjectNode range = filters.addObject().putObject("range").putObject("@timestamp");
        range.put("gte", defaultWindow.from().toString());
        range.put("lt", defaultWindow.to().toString());
        filters.addObject().putObject("ids").putArray("values").add(id);
        JsonNode response = executeSearch(body);
        JsonNode hits = response == null ? null : response.path("hits").path("hits");
        if (hits == null || !hits.isArray() || hits.isEmpty()) {
            throw new IllegalArgumentException("日志不存在");
        }
        JsonNode hit = hits.get(0);
        List<Object> sort = new ArrayList<>();
        hit.path("sort").forEach(value -> sort.add(objectMapper.convertValue(value, Object.class)));
        return new LogSearchResponse.LogHit(hit.path("_id").asText(id),
                hit.path("_index").asText(ingestProperties.getElasticsearchIndex()),
                hit.path("_source"), sort);
    }

    /** 查询目标日志前后上下文；traceId 存在时优先限定在同一 Trace。 */
    public LogSearchResponse context(String id, LogContextRequest request, boolean admin) {
        LogSearchResponse.LogHit target = detail(id, admin);
        JsonNode source = target.source();
        Instant timestamp = windowService.parseTimestamp(
                request != null && request.timestamp() != null
                        ? request.timestamp() : source.path("@timestamp").asText());
        int before = boundedSeconds(request == null ? null : request.beforeSeconds());
        int after = boundedSeconds(request == null ? null : request.afterSeconds());
        LogQueryWindowService.QueryWindow permission = windowService.resolve(
                timestamp.minusSeconds(before).toString(), timestamp.plusSeconds(after).toString(), admin);

        LogSearchRequest contextRequest = new LogSearchRequest(
                permission.from().toString(), permission.to().toString(),
                null, null,
                request != null && request.serviceName() != null
                        ? request.serviceName() : source.path("service.name").asText(null),
                null,
                request != null && request.traceId() != null
                        ? request.traceId() : source.path("trace.id").asText(null),
                null, null, null, null, null,
                queryProperties.getMaxContextSize(), null);
        return search(contextRequest, admin);
    }

    public JsonNode aggregate(LogAggregationRequest request, boolean admin) {
        if (request == null || request.query() == null) {
            throw new IllegalArgumentException("聚合请求不能为空");
        }
        List<String> groupBy = request.groupBy() == null ? List.of() : request.groupBy();
        if (groupBy.isEmpty() || groupBy.size() > 3) {
            throw new IllegalArgumentException("groupBy 至少一个字段且最多三个字段");
        }
        for (String field : groupBy) {
            if (!AGGREGATION_FIELDS.contains(field)) {
                throw new IllegalArgumentException("不支持的聚合字段: " + field);
            }
        }
        LogQueryWindowService.QueryWindow window = windowService.resolve(
                request.query().from(), request.query().to(), admin);
        int size = Math.min(normalizeSize(request.size()), 100);
        ObjectNode body = baseSearchBody(window, 0);
        body.set("query", buildQuery(request.query(), window));
        ObjectNode aggs = body.putObject("aggs");
        ObjectNode current = aggs;
        for (int index = 0; index < groupBy.size(); index++) {
            String field = groupBy.get(index);
            ObjectNode terms = current.putObject(index == 0 ? "groups" : "groups_" + index)
                    .putObject("terms");
            terms.put("field", field);
            terms.put("size", size);
            if (index < groupBy.size() - 1) {
                current = current.withObject(index == 0 ? "groups" : "groups_" + index)
                        .putObject("aggs");
            }
        }
        return executeSearch(body);
    }

    private ObjectNode baseSearchBody(LogQueryWindowService.QueryWindow window, int size) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", size);
        // 只精确统计到 10000，超过后由 ES 标记 relation=gte，避免大范围精确 count 拖慢查询。
        body.put("track_total_hits", 10000);
        body.put("timeout", queryProperties.getTimeout());
        ArrayNode sort = body.putArray("sort");
        sort.addObject().putObject("@timestamp").put("order", "desc");
        sort.addObject().putObject("_id").put("order", "desc");
        return body;
    }

    private ObjectNode buildQuery(LogSearchRequest request, LogQueryWindowService.QueryWindow window) {
        ObjectNode bool = objectMapper.createObjectNode().putObject("bool");
        ArrayNode filters = bool.putArray("filter");
        ObjectNode range = filters.addObject().putObject("range").putObject("@timestamp");
        range.put("gte", window.from().toString());
        range.put("lt", window.to().toString());
        if (request != null) {
            addTerm(filters, "project.id", request.projectId());
            addTerm(filters, "deployment.environment", request.deploymentEnvironment());
            addTerm(filters, "service.name", request.serviceName());
            addTerm(filters, "service.instance.id", request.serviceInstanceId());
            addTerm(filters, "trace.id", request.traceId());
            addTerm(filters, "span.id", request.spanId());
            addTerm(filters, "log.level", request.logLevel());
            addTerm(filters, "error.type", request.errorType());
            if (request.attributes() != null) {
                request.attributes().forEach((key, value) -> {
                    if (key != null && key.matches("[a-zA-Z0-9_.-]{1,80}") && value != null) {
                        addTerm(filters, "attributes." + key, String.valueOf(value));
                    }
                });
            }
            if (request.keyword() != null && !request.keyword().isBlank()) {
                ObjectNode queryString = bool.putObject("must").putObject("simple_query_string");
                queryString.put("query", request.keyword().trim());
                queryString.putArray("fields").add("message").add("error.stack_trace")
                        .add("log.logger").add("code.namespace").add("code.function");
                queryString.put("lenient", true);
            }
        }
        return bool;
    }

    private void addTerm(ArrayNode filters, String field, String value) {
        if (value != null && !value.isBlank()) {
            filters.addObject().putObject("term").put(field, value);
        }
    }

    private JsonNode executeSearch(ObjectNode body) {
        return restClient.post()
                .uri("/{index}/_search", ingestProperties.getElasticsearchIndex())
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    private LogSearchResponse toSearchResponse(JsonNode response, LogQueryWindowService.QueryWindow window) {
        List<LogSearchResponse.LogHit> items = new ArrayList<>();
        JsonNode hits = response == null ? null : response.path("hits").path("hits");
        if (hits != null && hits.isArray()) {
            hits.forEach(hit -> {
                List<Object> sort = new ArrayList<>();
                if (hit.path("sort").isArray()) {
                    hit.path("sort").forEach(value -> sort.add(objectMapper.convertValue(value, Object.class)));
                }
                items.add(new LogSearchResponse.LogHit(
                        hit.path("_id").asText(), hit.path("_index").asText(),
                        hit.path("_source"), sort));
            });
        }
        List<Object> next = items.isEmpty() ? List.of() : items.get(items.size() - 1).sort();
        long total = response == null ? 0 : response.path("hits").path("total").path("value").asLong(0);
        return new LogSearchResponse(items, next, total, window.from(), window.to());
    }

    private int normalizeSize(Integer size) {
        if (size == null) { return Math.min(50, queryProperties.getMaxPageSize()); }
        if (size < 1 || size > queryProperties.getMaxPageSize()) {
            throw new IllegalArgumentException("size 必须在 1 到 " + queryProperties.getMaxPageSize() + " 之间");
        }
        return size;
    }

    private void validateSearchAfter(List<Object> searchAfter) {
        // 当前排序固定为 @timestamp + _id，因此游标必须正好有两个值；
        // 不接受前端自行构造的多字段游标，避免绕过排序和产生 ES 解析异常。
        if (searchAfter.size() != 2) {
            throw new IllegalArgumentException("searchAfter 必须包含上一页返回的两个排序值");
        }
        if (searchAfter.get(0) == null || searchAfter.get(1) == null
                || String.valueOf(searchAfter.get(1)).length() > 512) {
            throw new IllegalArgumentException("searchAfter 游标无效");
        }
    }

    private int boundedSeconds(Integer seconds) {
        int value = seconds == null ? 30 : seconds;
        if (value < 0 || value > 300) {
            throw new IllegalArgumentException("上下文时间窗口必须在 0 到 300 秒之间");
        }
        return value;
    }
}
