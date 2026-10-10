package com.zhouyi.admin.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 预算审计投影文档 ID 的幂等性测试。 */
class FinopsAuditWriterTest {

    /** 相同 FinOps 事件 ID 必须映射到相同文档 ID。 */
    @Test
    void documentIdIsStable() {
        assertThat(FinopsAuditWriter.documentId("evt-1"))
                .isEqualTo(FinopsAuditWriter.documentId("evt-1"))
                .hasSize(64);
    }
}
