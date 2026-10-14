package com.zhouyi.admin.config;

import org.apache.kafka.clients.CommonClientConfigs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Kafka 环境变量到自定义客户端工厂的配置契约测试。 */
@SpringBootTest(properties = {
        "KAFKA_BOOTSTRAP_SERVERS=kafka-a:19092,kafka-b:29092",
        "KAFKA_SECURITY_PROTOCOL=PLAINTEXT",
        "LOG_CONSUMER_ENABLED=false",
        "FINOPS_BUDGET_AUDIT_CONSUMER_ENABLED=false",
        "spring.datasource.url=jdbc:h2:mem:kafka_config;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@ActiveProfiles("test")
class LogIngestConfigurationTest {

    @Autowired
    @Qualifier("logConsumerFactory")
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    @Qualifier("logProducerFactory")
    private ProducerFactory<String, String> producerFactory;

    @Autowired
    @Qualifier("logKafkaListenerContainerFactory")
    private ConcurrentKafkaListenerContainerFactory<String, String> listenerContainerFactory;

    /** KAFKA_BOOTSTRAP_SERVERS 必须同时进入消费和生产客户端。 */
    @Test
    void bindsEnvironmentBootstrapServersToKafkaFactories() {
        List<String> expected = List.of("kafka-a:19092", "kafka-b:29092");

        assertThat(consumerFactory.getConfigurationProperties())
                .containsEntry(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, expected);
        assertThat(producerFactory.getConfigurationProperties())
                .containsEntry(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, expected);
    }

    /** @KafkaListener 使用的容器必须关联上面的自定义 ConsumerFactory。 */
    @Test
    void listenerContainerUsesConfiguredConsumerFactory() {
        assertThat(listenerContainerFactory.getConsumerFactory()).isSameAs(consumerFactory);
    }
}
