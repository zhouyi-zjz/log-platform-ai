package com.zhouyi.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 字段脱敏测试，确保列表、详情共享同一套递归规则。 */
class LogFieldRedactorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LogFieldRedactor redactor = new LogFieldRedactor();

    @Test
    void masksCredentialsInNestedObjectsAndArrays() throws Exception {
        var source = objectMapper.readTree("""
                {"message":"ok","http":{"authorization":"Bearer secret"},
                 "attributes":{"api_token":"abc","items":[{"password":"p"}]}}
                """);

        var result = redactor.redact(source);

        assertThat(result.path("message").asText()).isEqualTo("ok");
        assertThat(result.path("http").path("authorization").asText()).isEqualTo("[REDACTED]");
        assertThat(result.path("attributes").path("api_token").asText()).isEqualTo("[REDACTED]");
        assertThat(result.path("attributes").path("items").get(0).path("password").asText())
                .isEqualTo("[REDACTED]");
        assertThat(source.path("http").path("authorization").asText()).isEqualTo("Bearer secret");
    }
}
