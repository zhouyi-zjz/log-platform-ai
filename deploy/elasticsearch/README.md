# Elasticsearch 8.x 初始化

`logs-index-template.json` 是字段模板示例。生产环境还需要创建 7 天 ILM 策略，并将 Data Stream、模板和策略纳入 IaC：

```http
PUT _ilm/policy/logs-7d
{
  "policy": {"phases": {"hot": {"actions": {}}, "delete": {"min_age": "7d", "actions": {"delete": {}}}}}
}
```

应用配置中的 `elasticsearch-index` 应指向 Data Stream 名称，例如 `logs-platform-default`。ES 服务端和客户端均按同一 8.x minor 固定。

## 旧数据补齐 event.id

升级到使用 `@timestamp + event.id` 游标的版本前，先更新模板和 Data Stream mapping，再为仍在
7 天保留期内的旧文档补齐稳定 ID：

```bash
curl -fsS -X POST \
  'http://localhost:9200/logs-platform-default/_update_by_query?conflicts=proceed&slices=auto' \
  -H 'Content-Type: application/json' \
  --data-binary @deploy/elasticsearch/backfill-event-id.json
```

回填使用原 ES `_id` 作为历史文档的 `event.id`。数据量较大时应在低峰执行并监控 task、磁盘和写入拒绝率；
新写入日志由平台直接生成稳定 `event.id`，不需要重复执行。
