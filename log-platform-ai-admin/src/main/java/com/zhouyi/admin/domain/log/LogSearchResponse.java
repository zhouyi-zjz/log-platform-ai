package com.zhouyi.admin.domain.log;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

/** 查询响应，保留 ES 原始 source，避免为了展示字段再次定义一套 DTO。 */
public record LogSearchResponse(
        /** 当前页日志。 */
        List<LogHit> items,
        /** 下一页 search_after 游标。 */
        List<Object> nextSearchAfter,
        /** ES 返回的命中总数（超过阈值时为下界）。 */
        long total,
        /** 服务端实际采用的起始时间。 */
        Instant effectiveFrom,
        /** 服务端实际采用的结束时间。 */
        Instant effectiveTo) {

    public record LogHit(
            /** ES 文档 ID。 */
            String id,
            /** ES backing index 名称。 */
            String index,
            /** 脱敏后的日志字段。 */
            JsonNode source,
            /** 当前文档的排序值。 */
            List<Object> sort) {
    }
}
