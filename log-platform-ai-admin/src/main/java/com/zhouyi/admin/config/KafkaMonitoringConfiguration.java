package com.zhouyi.admin.config;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.HashMap;
import java.util.Map;

/** 为 Kafka 自监控创建与业务 Producer/Consumer 隔离的 Admin 客户端。 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(KafkaMonitoringProperties.class)
public class KafkaMonitoringConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "log-platform.kafka-monitoring", name = "enabled", havingValue = "true")
    public Admin kafkaMonitoringAdmin(KafkaProperties kafkaProperties,
                                      KafkaMonitoringProperties monitoringProperties,
                                      ObjectProvider<SslBundles> sslBundles) {
        Map<String, Object> properties = new HashMap<>(
                kafkaProperties.buildAdminProperties(sslBundles.getIfAvailable()));
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        properties.put(AdminClientConfig.CLIENT_ID_CONFIG,
                (kafkaProperties.getClientId() == null ? "log-platform" : kafkaProperties.getClientId())
                        + "-monitor");
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG,
                Math.max(1000, monitoringProperties.getTimeoutMs()));
        properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG,
                Math.max(1000, monitoringProperties.getTimeoutMs()));
        return Admin.create(properties);
    }
}
