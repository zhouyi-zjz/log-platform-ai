package com.zhouyi.admin.domain.log;

import java.util.List;

/** 预算审计只读查询请求，不接受任意 Elasticsearch DSL。 */
public record BudgetAuditSearchRequest(
        /** 查询起始时间。 */
        String from,
        /** 查询结束时间。 */
        String to,
        /** 项目 ID。 */
        String projectId,
        /** 预算裁决结果。 */
        String decision,
        /** 预算预占 ID。 */
        String reservationId,
        /** Agent 运行 ID。 */
        String runId,
        /** 单页大小。 */
        Integer size,
        /** 上一页返回的游标。 */
        List<Object> searchAfter) {
}
