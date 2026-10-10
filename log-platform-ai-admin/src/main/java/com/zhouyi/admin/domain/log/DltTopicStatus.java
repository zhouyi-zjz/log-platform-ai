package com.zhouyi.admin.domain.log;

import java.util.List;

/** DLT Topic 的低敏状态，不返回消息正文。 */
public record DltTopicStatus(
        /** DLT Topic。 */
        String topic,
        /** 重放目标 Topic。 */
        String targetTopic,
        /** 各分区积压快照。 */
        List<PartitionStatus> partitions) {

    /** 单个分区的 offset 状态。 */
    public record PartitionStatus(int partition, long beginningOffset, long endOffset, long pendingRecords) {
    }
}
