package com.example.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "temporal")
public record TemporalProperties(
    String host,
    String namespace,
    String taskQueue,
    int maxConcurrentActivities,
    int maxConcurrentWorkflows) {}
