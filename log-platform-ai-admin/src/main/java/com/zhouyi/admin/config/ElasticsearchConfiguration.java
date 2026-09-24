package com.zhouyi.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/** Elasticsearch 8.x 的轻量 HTTP 客户端配置。实际生产可替换为官方 Java API Client。 */
@Configuration
public class ElasticsearchConfiguration {

    @Bean
    public RestClient elasticsearchRestClient(LogIngestProperties properties, LogQueryProperties queryProperties) {
        String endpoint = properties.getElasticsearchEndpoints().split(",")[0].trim();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(queryProperties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(queryProperties.getReadTimeoutMs());
        RestClient.Builder builder = RestClient.builder().baseUrl(endpoint).requestFactory(requestFactory);
        if (StringUtils.hasText(properties.getElasticsearchUsername())) {
            builder.defaultHeaders(headers -> headers.setBasicAuth(
                    properties.getElasticsearchUsername(), properties.getElasticsearchPassword()));
        }
        return builder.build();
    }
}
