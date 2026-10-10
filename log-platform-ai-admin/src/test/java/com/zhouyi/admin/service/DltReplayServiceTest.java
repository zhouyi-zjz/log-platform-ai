package com.zhouyi.admin.service;

import com.zhouyi.admin.config.FinopsIntegrationProperties;
import com.zhouyi.admin.config.LogIngestProperties;
import com.zhouyi.admin.domain.log.DltReplayRequest;
import org.apache.kafka.clients.consumer.ConsumerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** DLT 重放边界测试，确保运维接口不能变成任意 Topic 发布器。 */
class DltReplayServiceTest {

    private final DltReplayService service = new DltReplayService(
            mock(ConsumerFactory.class), mock(KafkaTemplate.class), new LogIngestProperties(),
            new FinopsIntegrationProperties(), mock(LogQueryAuditService.class),
            new PlatformMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));

    @Test
    void rejectsArbitraryTopic() {
        assertThatThrownBy(() -> service.replay(new DltReplayRequest("other.DLT", 0, 1, "test"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("不允许操作该 DLT Topic");
    }

    @Test
    void rejectsNegativePartitionAndOffset() {
        assertThatThrownBy(() -> service.replay(
                new DltReplayRequest("logs.raw.DLT", -1, 1, "test"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("partition 和 offset 必须为非负数");
        assertThatThrownBy(() -> service.replay(
                new DltReplayRequest("logs.raw.DLT", 0, -1, "test"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("partition 和 offset 必须为非负数");
    }
}
