package com.zhouyi.admin.domain.log;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** 预算审计只读查询响应。 */
public record BudgetAuditSearchResponse(
        /** 返回的审计事件。 */
        List<JsonNode> items,
        /** 命中总数，超过 ES 精确阈值时按下界解释。 */
        long total,
        /** 下一页游标。 */
        List<Object> nextSearchAfter) {
}
