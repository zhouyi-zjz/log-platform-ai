package com.zhouyi.admin.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
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
            org.springframework.boot.autoconfigure.kafka.KafkaProperties kafkaProperties) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildProducerProperties());
        properties.putIfAbsent(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        properties.putIfAbsent(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean
    public KafkaTemplate<String, String> logKafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConsumerFactory<String, String> logConsumerFactory(
            org.springframework.boot.autoconfigure.kafka.KafkaProperties kafkaProperties) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildConsumerProperties());
        properties.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(properties);
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
            CommonErrorHandler logKafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // 批量消费可以显著减少 ES Bulk 请求数量；一批中任意消息失败时整批重试。
        factory.setBatchListener(true);
        factory.setCommonErrorHandler(logKafkaErrorHandler);
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
}
