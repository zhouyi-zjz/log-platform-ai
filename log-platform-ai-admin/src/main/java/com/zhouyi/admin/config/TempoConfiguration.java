package com.zhouyi.admin.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 注册 Tempo 联查客户端，和 Elasticsearch 客户端分离以保持故障隔离。 */
@Configuration
@EnableConfigurationProperties(TempoProperties.class)
public class TempoConfiguration {

    /** 创建带有界超时的 Tempo REST 客户端。 */
    @Bean
    public RestClient tempoRestClient(TempoProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs());
        factory.setReadTimeout(properties.getTimeoutMs());
        return RestClient.builder().baseUrl(properties.getEndpoint()).requestFactory(factory).build();
    }
}
