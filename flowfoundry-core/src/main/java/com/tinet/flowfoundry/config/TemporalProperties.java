package com.tinet.flowfoundry.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Temporal cluster connection settings (host, concurrency, UI). Namespace and task queue come from Activity Registry. */
@ConfigurationProperties(prefix = "temporal")
public record TemporalProperties(
    String host,
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
