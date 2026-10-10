package com.zhouyi.admin.domain.log;

/** DLT 安全重放请求，只允许按固定 Topic、分区和 offset 重放单条消息。 */
public record DltReplayRequest(
        /** DLT Topic。 */
        String topic,
        /** Kafka 分区。 */
        int partition,
        /** Kafka offset。 */
        long offset,
        /** 操作原因，写入查询审计。 */
        String reason) {
}
