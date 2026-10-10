package com.zhouyi.common.domain.telemetry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** W3C Trace Context 格式和零值防护测试。 */
class W3cTraceContextTest {

    /** 合法 traceparent 应能稳定解析并原样生成标准头。 */
    @Test
    void parsesAndSerializesTraceparent() {
        W3cTraceContext context = W3cTraceContext.parse(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

        assertThat(context).isNotNull();
        assertThat(context.isValid()).isTrue();
        assertThat(context.traceparent())
                .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
    }

    /** 全零 Trace/Span 不能被接受为可信链路。 */
    @Test
    void rejectsZeroIds() {
        assertThat(W3cTraceContext.parse(
                "00-00000000000000000000000000000000-00f067aa0ba902b7-01")).isNull();
        assertThat(W3cTraceContext.parse(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01")).isNull();
    }
}
