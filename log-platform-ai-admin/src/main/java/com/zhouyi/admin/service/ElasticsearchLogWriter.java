package com.zhouyi.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouyi.admin.config.LogIngestProperties;
import com.zhouyi.common.domain.log.LogEvent;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

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

    public ElasticsearchLogWriter(RestClient elasticsearchRestClient,
                                  ObjectMapper objectMapper,
                                  LogIngestProperties properties) {
        this.restClient = elasticsearchRestClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void writeBulk(List<LogEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        try {
            StringBuilder body = new StringBuilder(events.size() * 256);
            for (LogEvent event : events) {
                body.append("{\"create\":{}}\n");
                body.append(objectMapper.writeValueAsString(event)).append('\n');
            }
            String response = restClient.post()
                    .uri("/{index}/_bulk", properties.getElasticsearchIndex())
                    .contentType(MediaType.parseMediaType("application/x-ndjson"))
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            JsonNode responseNode = objectMapper.readTree(response == null ? "{}" : response);
            if (responseNode.path("errors").asBoolean(false)) {
                throw new IllegalStateException("Elasticsearch Bulk 部分写入失败");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("日志序列化失败", exception);
        }
    }
}
