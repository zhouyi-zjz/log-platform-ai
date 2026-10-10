package com.zhouyi.admin.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class BootstrapAdminConfiguration {
}
