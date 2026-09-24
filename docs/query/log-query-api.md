# 日志检索 API

所有检索接口都要求登录，会从服务端会话读取 `isAdmin`，不信任请求体中的管理员标记。
查询 DSL 由后端根据白名单字段生成，调用方不能提交原始 Elasticsearch DSL。

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

排序固定为 `@timestamp desc, _id desc`，禁止使用深分页或自行修改排序。

## 日志详情

```http
GET /monitor/api/logs/{id}
```

详情查询仍然附带权限时间范围过滤，不能仅凭文档 ID 绕过普通用户当天限制。

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

## 超时与限流

- ES DSL 设置 `timeout=3s`，HTTP 读取超时默认 5 秒。
- 单用户/IP 默认每分钟最多 120 次查询。
- 全局默认最多 20 个并行查询，超过返回 `429`。
- ES 超时或连接失败返回 `504`/网关错误提示。
- 当前限流为单实例内存实现；多实例部署时应迁移到 Redis 或网关限流。
