package com.zhouyi.admin.domain.log;

import java.util.List;

/** 聚合请求复用检索条件，只额外指定要统计的白名单字段。 */
public record LogAggregationRequest(
        /** 复用时间和过滤条件的查询请求。 */
        LogSearchRequest query,
        /** 聚合字段白名单，最多三层。 */
        List<String> groupBy,
        /** 每层最多返回的 bucket 数。 */
        Integer size) {
}
