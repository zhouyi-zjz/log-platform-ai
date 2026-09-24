package com.zhouyi.admin.domain.log;

import java.util.List;

/** 聚合请求复用检索条件，只额外指定要统计的白名单字段。 */
public record LogAggregationRequest(LogSearchRequest query, List<String> groupBy, Integer size) {
}
