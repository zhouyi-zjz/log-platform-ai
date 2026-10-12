package com.zhouyi.admin.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kafka 采集链路配置。
 *
 * <p>Spring Boot 已经可以自动创建 KafkaTemplate，但这里显式声明生产者、
 * 消费者和批量监听容器，便于后续加入 SASL/TLS、死信 Topic 和监控指标。</p>
 */
@Configuration
@EnableConfigurationProperties(LogIngestProperties.class)
public class LogIngestConfiguration {

    @Bean
    public ProducerFactory<String, String> logProducerFactory(
            KafkaProperties kafkaProperties) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildProducerProperties());
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers(kafkaProperties));
        properties.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean
    public KafkaTemplate<String, String> logKafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConsumerFactory<String, String> logConsumerFactory(
            KafkaProperties kafkaProperties,
            LogIngestProperties ingestProperties) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildConsumerProperties());
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers(kafkaProperties));
        properties.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // 让 Bulk 的动作上限真正约束单次 poll，避免配置写了 1000 条而客户端仍按默认值取数。
        properties.putIfAbsent(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
                Math.max(1, ingestProperties.getBulkMaxActions()));
        return new DefaultKafkaConsumerFactory<>(properties);
    }

    /**
     * 显式把 spring.kafka.bootstrap-servers 绑定到自定义 Producer/Consumer Factory。
     * application.yml 会先把 KAFKA_BOOTSTRAP_SERVERS 映射到该 Spring Boot 属性。
     */
    private static List<String> bootstrapServers(KafkaProperties kafkaProperties) {
        List<String> configuredServers = kafkaProperties.getBootstrapServers();
        if (configuredServers == null) {
            throw new IllegalStateException("spring.kafka.bootstrap-servers 不能为空");
        }
        List<String> servers = configuredServers.stream()
                .map(String::trim)
                .filter(server -> !server.isEmpty())
                .toList();
        if (servers.isEmpty()) {
            throw new IllegalStateException("spring.kafka.bootstrap-servers 不能为空");
        }
        return servers;
    }

    @Bean
    public CommonErrorHandler logKafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        // 单条消息短暂失败时重试 3 次；仍失败时投递到原 Topic + .DLT，避免无限阻塞分区或静默丢失。
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new org.apache.kafka.common.TopicPartition(
                        record.topic() + ".DLT", record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
    }

    @Bean(name = "logKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> logKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            CommonErrorHandler logKafkaErrorHandler,
            LogIngestProperties properties) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // 批量消费可以显著减少 ES Bulk 请求数量；一批中任意消息失败时整批重试。
        factory.setBatchListener(true);
        factory.setCommonErrorHandler(logKafkaErrorHandler);
        factory.setConcurrency(1);
        factory.getContainerProperties().setIdleBetweenPolls(Math.max(0, properties.getBulkFlushIntervalMs()));
        return factory;
    }

    @Bean
    @ConditionalOnProperty(prefix = "log-platform.ingest", name = "topic-auto-create", havingValue = "true")
    public NewTopic logRawTopic(LogIngestProperties properties) {
        // 仅在 broker 允许自动建 Topic 时生效；生产环境建议使用 IaC 预先创建并设置副本数。
        return new NewTopic(properties.getKafkaTopic(), properties.getTopicPartitions(),
                properties.getTopicReplicationFactor());
    }

    @Bean
    @ConditionalOnProperty(prefix = "log-platform.ingest", name = "topic-auto-create", havingValue = "true")
    public NewTopic logDeadLetterTopic(LogIngestProperties properties) {
        return new NewTopic(properties.getKafkaTopic() + ".DLT", properties.getTopicPartitions(),
                properties.getTopicReplicationFactor());
    }

    /** 创建预算审计主 Topic，生产环境也可由 Kafka Operator 预先管理。 */
    @Bean
    @ConditionalOnProperty(prefix = "log-platform.ingest", name = "topic-auto-create", havingValue = "true")
    public NewTopic finopsBudgetAuditTopic(FinopsIntegrationProperties properties,
                                           LogIngestProperties ingestProperties) {
        return new NewTopic(properties.getBudgetAuditTopic(), ingestProperties.getTopicPartitions(),
                ingestProperties.getTopicReplicationFactor());
    }

    /** 创建预算审计 DLT，复用 Kafka 错误处理器的 topic + .DLT 约定。 */
    @Bean
    @ConditionalOnProperty(prefix = "log-platform.ingest", name = "topic-auto-create", havingValue = "true")
    public NewTopic finopsBudgetAuditDeadLetterTopic(FinopsIntegrationProperties properties,
                                                     LogIngestProperties ingestProperties) {
        return new NewTopic(properties.getBudgetAuditTopic() + ".DLT", ingestProperties.getTopicPartitions(),
                ingestProperties.getTopicReplicationFactor());
    }
}
