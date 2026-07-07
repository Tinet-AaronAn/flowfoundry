package com.tinet.flowfoundry.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "temporal")
public record TemporalProperties(
    String host,
    String namespace,
    String taskQueue,
    int maxConcurrentActivities,
    int maxConcurrentWorkflows,
    String uiBaseUrl) {

  public String resolvedUiBaseUrl() {
    if (uiBaseUrl == null || uiBaseUrl.isBlank()) {
      return "http://127.0.0.1:8080";
    }
    return uiBaseUrl.trim().replaceAll("/+$", "");
  }
}
