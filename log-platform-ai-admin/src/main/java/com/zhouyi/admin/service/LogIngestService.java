package com.zhouyi.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouyi.admin.config.LogIngestProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;

/**
 *
 * @author zjz
 * @version 1.0
 * @description: 将采集入口收到的原始 JSON 投递到 Kafka，不在 Web 请求线程中访问 ES
 * @date 2026/9/12
 */
@Service
public class LogIngestService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final LogIngestProperties properties;

    public LogIngestService(KafkaTemplate<String, String> kafkaTemplate,
                            ObjectMapper objectMapper,
                            LogIngestProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public CompletableFuture<?> publish(JsonNode payload) throws JsonProcessingException {
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("日志消息必须是 JSON 对象");
        }
        JsonNode projectId = payload.get("project.id");
        if (projectId == null || projectId.asText().isBlank()) {
            throw new IllegalArgumentException("project.id 不能为空");
        }
        String body = objectMapper.writeValueAsString(payload);
        // 使用项目、服务和环境作为稳定 key，既保证同一业务日志顺序，也避免单一项目独占分区。
        String key = String.join("|",
                text(payload, "project.id"),
                text(payload, "service.name"),
                text(payload, "deployment.environment"));
        return kafkaTemplate.send(properties.getKafkaTopic(), key, body);
    }

    /** Token 校验放在服务层，便于未来迁移到网关或为不同项目配置独立 Token。 */
    public boolean isValidToken(String suppliedToken) {
        String expected = properties.getApiToken();
        if (expected == null || expected.isBlank()) {
            return true;
        }
        if (suppliedToken == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }
}
