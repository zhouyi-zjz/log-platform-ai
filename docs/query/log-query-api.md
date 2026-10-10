# 日志检索 API

所有检索接口都要求登录，会从服务端会话读取 `isAdmin`，不信任请求体中的管理员标记。
查询 DSL 由后端根据白名单字段生成，调用方不能提交原始 Elasticsearch DSL。
普通用户的项目范围来自 `sys_user_project`：指定未授权项目返回 `403`，未指定项目时后端自动
添加全部已授权项目过滤；管理员可查询全部项目。该约束同时应用于搜索、详情、上下文和聚合。

## 查询日志

```http
POST /monitor/api/logs/search
Content-Type: application/json
```

请求示例：

```json
{
  "from": "2026-09-12T00:00:00+08:00",
  "to": "2026-09-12T23:59:59+08:00",
  "project.id": "billing",
  "service.name": "billing-api",
  "log.level": "ERROR",
  "keyword": "timeout payment",
  "size": 50
}
```

普通用户的有效时间自动裁剪到当天自然日；管理员自动裁剪到最近 168 小时。
请求时间范围与权限窗口没有交集时返回 `400`。单页默认 50 条，最大 500 条。

响应中的 `nextSearchAfter` 必须原样放入下一次请求：

```json
{
  "searchAfter": ["2026-09-12T09:30:00.123Z", "a8b1c2"],
  "size": 50
}
```

排序固定为 `@timestamp desc, event.id desc`，禁止使用深分页或自行修改排序。

## 日志详情

```http
GET /monitor/api/logs/{id}
```

详情查询仍然附带权限时间范围过滤，不能仅凭文档 ID 绕过普通用户当天限制。列表、详情和上下文返回前会递归脱敏
`password`、`token`、`secret`、`authorization`、`cookie` 等字段，原始敏感值不会进入浏览器。

## 上下文

```http
POST /monitor/api/logs/{id}/context
Content-Type: application/json

{
  "beforeSeconds": 30,
  "afterSeconds": 30
}
```

上下文默认前后各 30 秒，单侧最多 300 秒、最多返回 100 条；如果请求没有传 `traceId` 或 `serviceName`，
服务端会优先从目标日志中读取并限定同一 Trace/服务。

## 聚合

```http
POST /monitor/api/logs/aggregations
Content-Type: application/json

{
  "query": {
    "from": "2026-09-12T00:00:00+08:00",
    "to": "2026-09-12T23:59:59+08:00",
    "project.id": "billing"
  },
  "groupBy": ["service.name", "log.level"],
  "size": 20
}
```

聚合字段是白名单，最多同时分组 3 个字段；当前支持项目、环境、服务、实例、日志级别、错误类型、
 HTTP 方法、状态码和路由。

## 异步导出

导出请求复用搜索接口的过滤字段，不接受 `searchAfter` 和原始 Elasticsearch DSL：

```http
POST /monitor/api/logs/exports
Content-Type: application/json

{
  "from": "2026-09-12T00:00:00+08:00",
  "to": "2026-09-12T23:59:59+08:00",
  "project.id": "billing",
  "service.name": "billing-api",
  "size": 100
}
```

接口返回 `202` 和任务 ID。任务状态为 `QUEUED`、`RUNNING`、`SUCCEEDED`、`FAILED` 或
`EXPIRED`，通过以下接口轮询并下载：

```http
GET /monitor/api/logs/exports/{jobId}
GET /monitor/api/logs/exports/{jobId}/download
```

Worker 每页调用同一查询服务，因此继续执行项目 ACL、时间窗和字段脱敏；CSV 固定输出常用标识、服务、Trace、
级别、消息和异常字段，逗号/换行/引号按 CSV 规则转义。默认单任务最多 10,000 行、50 MiB，成功结果保留 30 分钟。
普通用户只能查看自己的任务，管理员可查看全部任务；当前结果保存在应用内存，多副本和对象存储持久化待后续实现。

## 超时与限流

- ES DSL 设置 `timeout=3s`，HTTP 读取超时默认 5 秒。
- 单用户/IP 默认每分钟最多 120 次查询。
- 全局默认最多 20 个并行查询，超过返回 `429`。
- ES 超时或连接失败返回 `504`/网关错误提示。
- 成功的搜索、详情和聚合会写入 `log_query_audit`，仅记录用户、操作、项目、耗时和受限备注，不保存关键字和日志正文。
- 默认使用单实例内存限流；设置 `LOG_QUERY_DISTRIBUTED_RATE_LIMIT=true` 并配置 Redis 后使用共享分钟窗口。
- Actuator 指标可从 `/actuator/prometheus` 获取，包含入口、消费、Bulk、查询和导出计数/延迟；Prometheus 抓取与告警规则需在目标环境验收。
