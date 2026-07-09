package com.tinet.flowfoundry.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flowfoundry.bff")
public class FlowFoundryBffProperties {

  private boolean enabled = true;
  private String basePath = "/app/api/flowfoundry";

  public boolean enabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String basePath() {
    if (basePath == null || basePath.isBlank()) {
      return "/app/api/flowfoundry";
    }
    String normalized = basePath.trim();
    if (!normalized.startsWith("/")) {
      normalized = "/" + normalized;
    }
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  public void setBasePath(String basePath) {
    this.basePath = basePath;
  }
}
