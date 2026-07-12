package com.tinet.flowfoundry.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Plugin manager settings. {@code dir} is the local package store root (P1; see design doc §12). */
@ConfigurationProperties(prefix = "flowfoundry.plugins")
public record PluginProperties(String dir, Integer maxReplicas) {

  private static final int DEFAULT_MAX_REPLICAS = 50;

  public String resolvedDir() {
    if (dir == null || dir.isBlank()) {
      return System.getProperty("user.home") + "/.flowfoundry/plugins";
    }
    return dir.trim();
  }

  public int resolvedMaxReplicas() {
    return maxReplicas == null || maxReplicas < 1 ? DEFAULT_MAX_REPLICAS : maxReplicas;
  }
}
