package com.tinet.flowfoundry.activity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "activity.script-runtime")
public record ScriptRuntimeProperties(
    String serviceUrl, Integer timeoutSeconds, String enterpriseId, String sourceType) {

  public int resolvedTimeoutSeconds() {
    return timeoutSeconds == null || timeoutSeconds <= 0 ? 30 : timeoutSeconds;
  }

  public int resolvedRunTimeoutMs() {
    int seconds = resolvedTimeoutSeconds();
    return Math.min(seconds * 1000, 10_000);
  }

  public String resolvedSourceType() {
    return sourceType == null || sourceType.isBlank() ? "flowfoundry" : sourceType.trim();
  }
}
