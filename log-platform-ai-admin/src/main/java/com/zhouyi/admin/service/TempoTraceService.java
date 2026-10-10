package com.zhouyi.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhouyi.admin.config.TempoProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Set;

/**
 * Tempo Trace 查询适配器。
 *
 * <p>Trace 查询只是日志平台的关联视图，不改变日志权限和 FinOps 事实；Tempo 不可用时返回
 * 空结果并记录低基数失败指标，不能阻塞日志检索。</p>
 */
@Service
public class TempoTraceService {

    private static final Set<String> NANOSECOND_FIELDS = Set.of(
            "startTimeUnixNano", "start_time_unix_nano", "durationNanos", "duration_nanos");

    private final RestClient restClient;
    private final TempoProperties properties;
    private final Counter lookupSuccess;
    private final Counter lookupFailure;

    /** 创建 Tempo 适配器并注册健康指标。 */
    public TempoTraceService(@Qualifier("tempoRestClient") RestClient restClient,
                             TempoProperties properties, MeterRegistry registry) {
        this.restClient = restClient;
        this.properties = properties;
        this.lookupSuccess = Counter.builder("log_platform_tempo_lookup_success_total")
                .description("Tempo Trace 联查成功次数").register(registry);
        this.lookupFailure = Counter.builder("log_platform_tempo_lookup_failure_total")
                .description("Tempo Trace 联查失败或未启用次数").register(registry);
    }

    /** 查询指定 Trace；未启用、非法 ID 或后端故障均按 fail-open 返回空节点。 */
    public JsonNode find(String traceId) {
        if (!properties.isEnabled() || !isTraceId(traceId)) {
            lookupFailure.increment();
            return NullNode.getInstance();
        }
        try {
            JsonNode result = restClient.get().uri("/api/traces/{traceId}", traceId)
                    .retrieve().body(JsonNode.class);
            lookupSuccess.increment();
            // JSON numbers above 2^53 lose nanosecond precision in browser clients. Keep the
            // canonical integer fields as strings so the portal can calculate with BigInt.
            return result == null ? NullNode.getInstance() : preserveNanosecondFields(result);
        } catch (RuntimeException exception) {
            lookupFailure.increment();
            return NullNode.getInstance();
        }
    }

    /** 校验 Tempo API 所需的 32 位十六进制 Trace ID。 */
    private static boolean isTraceId(String traceId) {
        return traceId != null && traceId.matches("[0-9a-fA-F]{32}")
                && !traceId.chars().allMatch(character -> character == '0');
    }

    /** 将 Tempo 常见纳秒时间字段序列化为字符串，避免跨 JSON/JavaScript 的整数精度损失。 */
    private static JsonNode preserveNanosecondFields(JsonNode source) {
        JsonNode copy = source.deepCopy();
        normalizeNanosecondFields(copy);
        return copy;
    }

    private static void normalizeNanosecondFields(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.fieldNames().forEachRemaining(field -> {
                JsonNode value = object.get(field);
                if (NANOSECOND_FIELDS.contains(field) && value != null && value.isIntegralNumber()) {
                    object.put(field, value.asText());
                } else {
                    normalizeNanosecondFields(value);
                }
            });
        } else if (node instanceof ArrayNode array) {
            array.forEach(TempoTraceService::normalizeNanosecondFields);
        }
    }
}
