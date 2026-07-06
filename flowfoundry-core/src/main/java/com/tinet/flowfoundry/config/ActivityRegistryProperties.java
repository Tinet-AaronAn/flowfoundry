package com.tinet.flowfoundry.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.activity-registry")
public record ActivityRegistryProperties(String path) {}
