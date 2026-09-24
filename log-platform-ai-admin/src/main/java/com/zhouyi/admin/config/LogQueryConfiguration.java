package com.zhouyi.admin.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 查询专用配置：复用同一个 ES 地址，但把连接/读取超时显式设置，避免请求无限挂起。 */
@Configuration
@EnableConfigurationProperties(LogQueryProperties.class)
public class LogQueryConfiguration {
}
