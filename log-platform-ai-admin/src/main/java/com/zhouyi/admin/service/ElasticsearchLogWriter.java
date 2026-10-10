package com.zhouyi.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouyi.admin.config.LogIngestProperties;
import com.zhouyi.common.domain.log.LogEvent;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * <p>Data Stream 要求使用 {@code create} 操作，因此这里统一使用 create；
 * 普通索引同样可以接受该操作。Bulk 返回体中的 partial failure 会抛出异常，
 * 让 Kafka 监听器重试，而不是静默丢失单条日志。</p>
 *
 * @author zjz
 * @version 1.0
 * @description: 负责把规范化后的日志以 Elasticsearch Bulk NDJSON 写入 Data Stream。
 * @date 2026/9/12
 */
@Service
public class ElasticsearchLogWriter {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final LogIngestProperties properties;
    private final PlatformMetrics metrics;

    public ElasticsearchLogWriter(RestClient elasticsearchRestClient,
                                  ObjectMapper objectMapper,
                                  LogIngestProperties properties) {
        this(elasticsearchRestClient, objectMapper, properties, null);
    }

    /** 创建带 Bulk 延迟与失败埋点的 ES 写入服务。 */
    @Autowired
    public ElasticsearchLogWriter(RestClient elasticsearchRestClient,
                                  ObjectMapper objectMapper,
                                  LogIngestProperties properties,
                                  PlatformMetrics metrics) {
        this.restClient = elasticsearchRestClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.metrics = metrics;
    }

    /** 将一批规范化日志写入 ES，并把部分失败交给 Kafka 重试。 */
    public void writeBulk(List<LogEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        long sample = metrics == null ? 0 : metrics.startBulk();
        try {
            String body = buildBulkBody(events);
            String response = restClient.post()
                    .uri("/{index}/_bulk", properties.getElasticsearchIndex())
                    .contentType(MediaType.parseMediaType("application/x-ndjson"))
                    .body(body)
                    .retrieve()
                    .body(String.class);
            validateBulkResponse(response);
        } catch (JsonProcessingException exception) {
            if (metrics != null) {
                metrics.markBulkFailure();
            }
            throw new IllegalStateException("日志序列化失败", exception);
        } catch (RuntimeException exception) {
            if (metrics != null) {
                metrics.markBulkFailure();
            }
            throw exception;
        } finally {
            if (metrics != null) {
                metrics.recordBulk(sample);
            }
        }
    }

    String buildBulkBody(List<LogEvent> events) throws JsonProcessingException {
        StringBuilder body = new StringBuilder(events.size() * 320);
        for (LogEvent event : events) {
            if (event.eventId() == null || event.eventId().isBlank()) {
                throw new IllegalArgumentException("event.id 不能为空");
            }
            if (event.projectId() == null || event.projectId().isBlank()) {
                throw new IllegalArgumentException("project.id 不能为空");
            }
            body.append("{\"create\":{\"_id\":")
                    .append(objectMapper.writeValueAsString(documentId(event)))
                    .append("}}\n");
            body.append(objectMapper.writeValueAsString(event)).append('\n');
        }
        return body.toString();
    }

    /** 共享 Data Stream 中必须把项目纳入文档 ID，避免不同项目使用相同 event.id 时互相冲突。 */
    static String documentId(LogEvent event) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = (event.projectId() + "\0" + event.eventId()).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }

    void validateBulkResponse(String response) throws JsonProcessingException {
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("Elasticsearch Bulk 返回空响应");
        }
        JsonNode responseNode = objectMapper.readTree(response);
        if (!responseNode.has("errors")) {
            throw new IllegalStateException("Elasticsearch Bulk 响应缺少 errors 字段");
        }
        if (!responseNode.path("errors").asBoolean(false)) {
            return;
        }
        List<String> failures = new ArrayList<>();
        JsonNode items = responseNode.path("items");
        if (!items.isArray() || items.isEmpty()) {
            throw new IllegalStateException("Elasticsearch Bulk 返回失败但缺少 items");
        }
        for (JsonNode item : items) {
            JsonNode create = item.path("create");
            int status = create.path("status").asInt(0);
            // 同一项目的相同 event.id 在 Kafka 重试时会返回 409，说明该事件已经成功写入。
            if ((status >= 200 && status < 300) || status == 409) {
                continue;
            }
            failures.add(create.path("_id").asText("unknown") + ":" + status);
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Elasticsearch Bulk 部分写入失败: " + String.join(",", failures));
        }
    }
}
