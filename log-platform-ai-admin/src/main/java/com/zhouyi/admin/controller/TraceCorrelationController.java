package com.zhouyi.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.zhouyi.admin.domain.log.LogSearchRequest;
import com.zhouyi.admin.domain.log.LogSearchResponse;
import com.zhouyi.admin.domain.log.ProjectAccessScope;
import com.zhouyi.admin.service.LogQueryService;
import com.zhouyi.admin.service.ProjectAuthorizationService;
import com.zhouyi.admin.service.TempoTraceService;
import com.zhouyi.common.domain.AjaxResult;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供受 ACL 和时间窗口约束的日志/Tempo Trace 联查接口。 */
@RestController
@RequestMapping("/monitor/api/traces")
public class TraceCorrelationController {

    private final LogQueryService queryService;
    private final ProjectAuthorizationService authorizationService;
    private final TempoTraceService tempoTraceService;

    /** 创建 Trace 联查控制器。 */
    public TraceCorrelationController(LogQueryService queryService,
                                      ProjectAuthorizationService authorizationService,
                                      TempoTraceService tempoTraceService) {
        this.queryService = queryService;
        this.authorizationService = authorizationService;
        this.tempoTraceService = tempoTraceService;
    }

    /** 查询 Trace 对应日志和 Tempo 原始 Trace；Tempo 故障不影响日志部分。 */
    @GetMapping("/{traceId}")
    public AjaxResult<TraceCorrelation> find(@PathVariable String traceId,
                                             Authentication authentication) {
        if (traceId == null || !traceId.matches("[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException("Trace ID 无效");
        }
        ProjectAccessScope scope = authorizationService.resolve(authentication);
        LogSearchResponse logs = queryService.search(new LogSearchRequest(
                null, null, null, null, null, null, traceId,
                null, null, null, null, null, 200, null), scope);
        JsonNode trace = tempoTraceService.find(traceId);
        return AjaxResult.success(new TraceCorrelation(traceId, logs,
                trace == null ? NullNode.getInstance() : trace));
    }

    /** 日志与 Trace 的联合响应模型。 */
    public record TraceCorrelation(
            /** W3C Trace ID。 */
            String traceId,
            /** 受服务端 ACL/时间窗约束的日志结果。 */
            LogSearchResponse logs,
            /** Tempo Trace 原始结构；未启用或失败时为空对象。 */
            JsonNode tempoTrace) {
    }
}
