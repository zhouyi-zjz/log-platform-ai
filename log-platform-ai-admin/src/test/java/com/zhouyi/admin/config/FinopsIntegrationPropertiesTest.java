package com.zhouyi.admin.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** FinOps 集成默认安全边界测试。 */
class FinopsIntegrationPropertiesTest {

    /** 默认配置不得主动连接独立 FinOps 服务。 */
    @Test
    void defaultsToDisabledMetadataOnlyFailOpen() {
        FinopsIntegrationProperties properties = new FinopsIntegrationProperties();

        assertThat(properties.getMode()).isEqualTo(FinopsIntegrationProperties.Mode.DISABLED);
        assertThat(properties.isMetadataOnly()).isTrue();
        assertThat(properties.isFailOpen()).isTrue();
        assertThat(properties.isActive()).isFalse();
    }

    /** 外部调用超时必须有上限，避免错误配置阻塞日志链路。 */
    @Test
    void boundsExternalTimeout() {
        FinopsIntegrationProperties properties = new FinopsIntegrationProperties();
        properties.setTimeoutMs(99_999);

        assertThat(properties.getTimeoutMs()).isEqualTo(10_000);
    }
}
