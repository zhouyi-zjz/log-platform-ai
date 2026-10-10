package com.zhouyi.common.domain.telemetry;

import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

/** SDK 无关异步传播端口测试。 */
class AsyncTracePropagationTest {

    /** Temporal/RocketMQ 风格的 Metadata Map 应保持统一 Trace 和 request 关联。 */
    @Test
    void propagatesThroughGenericHeaders() {
        var headers = new HashMap<String, String>();
        AsyncTracePropagation.inject(headers, new TelemetryContext(null, "p", "s", "prod",
                "request-1", "4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", null));

        var context = AsyncTracePropagation.extract(headers);

        assertThat(context).isNotNull();
        assertThat(context.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(context.requestId()).isEqualTo("request-1");
    }
}
