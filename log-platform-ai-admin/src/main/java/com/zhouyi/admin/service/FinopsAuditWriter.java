package com.zhouyi.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhouyi.admin.config.FinopsIntegrationProperties;
import com.zhouyi.common.domain.telemetry.BudgetAuditEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 将预算审计事件写入独立 Elasticsearch 索引；日志平台不修改 FinOps 权威事实。 */
@Service
public class FinopsAuditWriter {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final FinopsIntegrationProperties properties;
    private final Counter written;
    private final Counter duplicates;
    private final Counter failures;

    /** 创建预算审计写入器并注册低基数指标。 */
    public FinopsAuditWriter(@Qualifier("elasticsearchRestClient") RestClient restClient,
                             ObjectMapper objectMapper,
                             FinopsIntegrationProperties properties,
                             MeterRegistry registry) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.written = Counter.builder("log_platform_finops_audit_written_total")
                .description("写入预算审计只读投影的事件数").register(registry);
        this.duplicates = Counter.builder("log_platform_finops_audit_duplicates_total")
                .description("预算审计投影幂等冲突数").register(registry);
        this.failures = Counter.builder("log_platform_finops_audit_write_failures_total")
                .description("预算审计投影写入失败数").register(registry);
    }

    /** 以 create 语义写入独立索引，重复事件视为幂等成功。 */
    public void write(BudgetAuditEvent event) {
        try {
            ObjectNode document = objectMapper.valueToTree(event);
            document.put("@timestamp", event.occurredAt().toString());
            document.put("event.ingested", java.time.Instant.now().toString());
            document.put("event.channel", "BUDGET_AUDIT");
            document.put("log.kind", "finops_budget_audit");
            String body = "{\"create\":{\"_id\":"
                    + objectMapper.writeValueAsString(documentId(event.eventId())) + "}}\n"
                    + objectMapper.writeValueAsString(document) + "\n";
            String response = restClient.post()
                    .uri("/{index}/_bulk", properties.getBudgetAuditIndex())
                    .contentType(MediaType.parseMediaType("application/x-ndjson"))
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (response == null || response.isBlank()) {
                throw new IllegalStateException("预算审计投影返回空响应");
            }
            var responseNode = objectMapper.readTree(response);
            if (!responseNode.path("errors").asBoolean(false)) {
                written.increment();
                return;
            }
            int status = responseNode.path("items").path(0).path("create")
                    .path("status").asInt(0);
            if (status == 409) {
                duplicates.increment();
                return;
            }
            throw new IllegalStateException("预算审计投影写入失败: " + status);
        } catch (JsonProcessingException | RuntimeException exception) {
            failures.increment();
            throw new IllegalStateException("预算审计投影写入失败", exception);
        }
    }

    /** 使用 FinOps 事件 ID 生成稳定文档 ID，避免重复消费产生重复投影。 */
    static String documentId(String eventId) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(eventId.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }
}
