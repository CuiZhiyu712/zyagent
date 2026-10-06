package com.zyagent.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ZyagentProperties.class)
public class PropertiesConfig {
}
