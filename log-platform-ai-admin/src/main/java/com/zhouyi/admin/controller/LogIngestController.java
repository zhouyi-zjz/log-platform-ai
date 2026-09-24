package com.zhouyi.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhouyi.admin.service.LogIngestService;
import com.zhouyi.common.domain.AjaxResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 面向 Fluent Bit、Vector 或其他采集器的轻量 HTTP 入口。
 *
 * <p>生产环境推荐采集器直接写 Kafka；保留该接口是为了兼容无法安装 Kafka
 * 输出插件的项目，以及方便本地联调。接口只负责鉴权、基础校验和入 Kafka，
 * 不同步等待 Elasticsearch。</p>
 */
@RestController
@RequestMapping("/monitor/api/logs")
public class LogIngestController {

    private final LogIngestService ingestService;
    private final ObjectMapper objectMapper;
    private final com.zhouyi.admin.config.LogIngestProperties properties;

    public LogIngestController(LogIngestService ingestService,
                               ObjectMapper objectMapper,
                               com.zhouyi.admin.config.LogIngestProperties properties) {
        this.ingestService = ingestService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @PostMapping(value = "/ingest", consumes = "application/json")
    public ResponseEntity<AjaxResult<IngestAccepted>> ingest(
            @RequestBody JsonNode payload,
            @RequestHeader(value = "X-Log-Ingest-Token", required = false) String token) {
        try {
            String raw = objectMapper.writeValueAsString(payload);
            if (raw.getBytes(StandardCharsets.UTF_8).length > properties.getMaxPayloadBytes()) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                        .body(AjaxResult.error(HttpStatus.PAYLOAD_TOO_LARGE.value(), "日志消息超过大小限制"));
            }
            if (!ingestService.isValidToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(AjaxResult.error(HttpStatus.UNAUTHORIZED.value(), "采集 Token 无效"));
            }
            var future = ingestService.publish(payload);
            // send 返回 Future，响应只表示已提交 Kafka，不代表 ES 已经写入成功。
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(AjaxResult.success("日志已提交", new IngestAccepted(
                            properties.getKafkaTopic(), future != null)));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(AjaxResult.error(HttpStatus.BAD_REQUEST.value(), exception.getMessage()));
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(AjaxResult.error(HttpStatus.SERVICE_UNAVAILABLE.value(), "日志暂时无法接收"));
        }
    }

    public record IngestAccepted(String topic, boolean accepted) {
    }
}
