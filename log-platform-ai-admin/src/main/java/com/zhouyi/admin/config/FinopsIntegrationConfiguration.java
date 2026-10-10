package com.zhouyi.admin.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 注册独立 FinOps 集成边界配置，不在日志平台创建预算或账本 Bean。 */
@Configuration
@EnableConfigurationProperties(FinopsIntegrationProperties.class)
public class FinopsIntegrationConfiguration {
}
