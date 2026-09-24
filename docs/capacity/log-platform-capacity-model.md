# 日志平台容量模型

## 1. 输入参数

| 参数 | 符号 | 当前值 | 说明 |
|---|---:|---:|---|
| 接入项目数 | `P` | 5 | 当前已知项目数 |
| 每项目日日志量上限 | `E_day_project` | 1,000,000 条/天 | 将“百万级”转为可计算的保守上限 |
| 总日事件量 | `E_day` | 5,000,000 条/天 | `P × E_day_project` |
| 平均事件大小（压缩前） | `B_event` | 1.5 KiB | 初始假设；需用真实样本校准 |
| Kafka/ES 副本数 | `R` | 2 | 1 主 + 1 副本；按生产下限建模 |
| ES 存储放大系数 | `F_es` | 1.5 | 倒排索引、doc values、元数据的经验预算 |
| Kafka 保留窗口 | `T_kafka` | 2 天 | 日志已写入 ES 后仍保留可重放窗口 |
| 日志保留期 | `T_retention` | 7 天 | 第 8 天删除 |
| 平均 EPS | `EPS_avg` | 57.9 | `5,000,000 / 86,400` |
| 设计峰值 EPS | `EPS_peak_design` | 300 | 暂按平均值 5 倍；真实峰值未提供 |
| 安全余量 | `F_headroom` | 1.5 | 预留增长、重平衡和查询写放大 |

> 若每项目实际不是 100 万条/天，请直接替换 `E_day_project`。若拿到真实峰值 EPS，替换 `EPS_peak_design`，并优先按峰值结果校验 Kafka 分区和 ES 写入能力。

## 2. 计算结果

### 2.1 事件吞吐

```text
E_day = P × E_day_project
      = 5 × 1,000,000
      = 5,000,000 条/天

EPS_avg = E_day / 86,400
        ≈ 57.9 EPS

设计峰值写入 = 300 EPS
```

每秒原始字节吞吐：

```text
Bytes_sec = EPS_peak_design × B_event
          = 300 × 1.5 KiB
          ≈ 450 KiB/s
          ≈ 0.44 MiB/s
```

每天原始日志量：

```text
Raw_day = E_day × B_event
        = 5,000,000 × 1.5 KiB
        ≈ 7.15 GiB/天
```

### 2.2 Elasticsearch 存储

7 天主数据（不含副本）：

```text
ES_primary_7d = Raw_day × T_retention × F_es
              = 7.15 × 7 × 1.5
              ≈ 75.1 GiB
```

考虑 1 副本和 50% 安全余量：

```text
ES_total_7d = ES_primary_7d × R × F_headroom
            = 75.1 × 2 × 1.5
            ≈ 225.3 GiB
```

因此生产集群应至少准备 **300 GiB 可用数据盘**，并建议按 3 个 data 节点均衡分配至少 `3 × 150 GiB`，而不是只采购理论最低值。若真实事件平均大小达到 3 KiB，存储估算近似翻倍。

建议初始分片（需要压测校准）：

- 共享 Data Stream 每个 backing index 1～2 primary shard。
- 3 个 data 节点、1 副本时，避免为 5 个项目分别建每日索引。
- 单 shard 目标控制在 10～30 GiB；7 天总量较小时优先少分片，避免小分片开销。

### 2.3 Kafka 存储与分区

Kafka 两天原始保留量：

```text
Kafka_primary_2d = Raw_day × T_kafka
                  ≈ 14.3 GiB

Kafka_total_2d = Kafka_primary_2d × R × F_headroom
               ≈ 42.9 GiB
```

建议生产至少准备 **100 GiB/broker** 的 Kafka 数据盘，保留突发积压、重放和 broker 故障迁移空间。300 EPS 下，按单分区 500～1,000 EPS 的保守目标，建议日志 Topic 起步 **6 分区**（可按 12 分区预留），3 broker 均衡副本因子 2；真实峰值 EPS 确认后再按以下公式复核：

```text
partitions >= ceil(EPS_peak_design / target_eps_per_partition)
```

其中 `target_eps_per_partition` 初始取 500，且必须以压测结果为准。

### 2.4 消费者与网络

- 日志规范化消费者：起步 2 个实例，每实例 2 个并发 consumer；确保实例数不超过 Topic 分区数。
- ES bulk：建议 500～2,000 条/批或 5～15 MiB/批，以较先达到的条件 flush。
- 写入端设置指数退避、最大重试次数和死信 Topic；不得无限重试阻塞分区。
- 300 EPS、1.5 KiB/事件的业务侧入口流量约 0.44 MiB/s；按 Kafka、ES 副本和重试放大，平台内部网络按 **5 MiB/s 持续、20 MiB/s 突发** 预留。

### 2.5 查询容量

查询容量不能只由 EPS 推导，需要单独压测。MVP 先按以下目标验收：

- 24 小时普通查询 P95 < 2 秒（普通用户实际只允许当天窗口）。
- 管理员 7 天查询 P95 < 5 秒。
- 同时活跃查询 20 个，单查询最多 500 条。
- 导出不占用同步查询线程，且具备每用户/每项目并发上限。

## 3. 规模变化换算

### 按项目数变化

```text
Raw_day_new = project_count × daily_events_per_project × avg_event_size
```

在其他参数不变时，ES 和 Kafka 磁盘需求与项目数线性增长。

### 按平均事件大小变化

```text
ES_total_new ≈ 225.3 GiB × (B_event_new / 1.5 KiB)
```

### 按峰值 EPS 变化

峰值 EPS 主要影响 Kafka 分区、消费者并发、ES bulk 队列和网络；若日总量不变，它不会显著改变 7 天存储量。

## 4. 扩容阈值

- Kafka consumer lag 连续 5 分钟 > 30 秒：先增加消费者实例/分区，再检查 ES bulk 拒绝率。
- ES 写入拒绝率 > 1% 或 bulk P95 > 2 秒：降低批量、增加 data 节点或扩展写入线程池，禁止盲目增加分片。
- ES 磁盘使用率 > 70%：扩容；> 85%：停止非必要导出并立即处理容量；> 90%：进入保护模式。
- 查询 P95 超过目标 3 个连续窗口：检查时间范围、字段 mapping、慢查询和缓存，再调整节点。

## 5. 必须用真实数据重新校准的参数

1. 各项目过去 7～14 天的每日事件量和每秒峰值。
2. 真实 JSON 日志的 p50/p95/p99 字节大小，尤其是异常堆栈。
3. 日志压缩、ES mapping 和副本后的实际存储放大系数。
4. 同时在线用户、查询比例、导出比例和最长查询窗口。
5. Kafka 故障时可接受的积压时长，以及是否需要 2 天以上重放窗口。
