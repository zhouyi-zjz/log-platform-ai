package com.zhouyi.admin.domain.log;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

/** 查询响应，保留 ES 原始 source，避免为了展示字段再次定义一套 DTO。 */
public record LogSearchResponse(
        List<LogHit> items,
        List<Object> nextSearchAfter,
        long total,
        Instant effectiveFrom,
        Instant effectiveTo) {

    public record LogHit(String id, String index, JsonNode source, List<Object> sort) {
    }
}
