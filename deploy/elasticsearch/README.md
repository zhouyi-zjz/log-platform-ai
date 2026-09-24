# Elasticsearch 8.x 初始化

`logs-index-template.json` 是字段模板示例。生产环境还需要创建 7 天 ILM 策略，并将 Data Stream、模板和策略纳入 IaC：

```http
PUT _ilm/policy/logs-7d
{
  "policy": {"phases": {"hot": {"actions": {}}, "delete": {"min_age": "7d", "actions": {"delete": {}}}}}
}
```

应用配置中的 `elasticsearch-index` 应指向 Data Stream 名称，例如 `logs-platform-default`。ES 服务端和客户端均按同一 8.x minor 固定。
