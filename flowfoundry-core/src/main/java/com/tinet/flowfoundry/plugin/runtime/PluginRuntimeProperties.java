package com.tinet.flowfoundry.plugin.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Kubernetes runtime settings for plugin runner Deployments. */
@ConfigurationProperties(prefix = "flowfoundry.plugins.runtime")
public record PluginRuntimeProperties(
    Boolean enabled,
    String kubernetesNamespace,
    String runnerImage,
    String platformUrl,
    String downloadTokenSecret,
    Integer reconcileIntervalMs,
    Integer terminationGraceSeconds,
    String redisHost,
    String runnerTemporalHost) {

  private static final String DEFAULT_NAMESPACE = "flowfoundry-plugins";
  private static final String DEFAULT_IMAGE = "flowfoundry-plugin-runner:local";
  private static final String DEFAULT_PLATFORM_URL = "http://host.docker.internal:8081";
  private static final String DEFAULT_SECRET = "local-plugin-download-secret";

  public boolean isRuntimeEnabled() {
    return Boolean.TRUE.equals(enabled);
  }

  public String resolvedKubernetesNamespace() {
    return kubernetesNamespace == null || kubernetesNamespace.isBlank()
        ? DEFAULT_NAMESPACE
        : kubernetesNamespace.trim();
  }

  public String resolvedRunnerImage() {
    return runnerImage == null || runnerImage.isBlank() ? DEFAULT_IMAGE : runnerImage.trim();
  }

  public String resolvedPlatformUrl() {
    if (platformUrl == null || platformUrl.isBlank()) {
      return DEFAULT_PLATFORM_URL;
    }
    return platformUrl.trim().replaceAll("/+$", "");
  }

  public String resolvedDownloadTokenSecret() {
    return downloadTokenSecret == null || downloadTokenSecret.isBlank()
        ? DEFAULT_SECRET
        : downloadTokenSecret;
  }

  public int resolvedReconcileIntervalMs() {
    return reconcileIntervalMs == null || reconcileIntervalMs < 1000 ? 15000 : reconcileIntervalMs;
  }

  public int resolvedTerminationGraceSeconds() {
    return terminationGraceSeconds == null || terminationGraceSeconds < 1 ? 60 : terminationGraceSeconds;
  }

  public String resolvedRedisHost() {
    return redisHost == null || redisHost.isBlank() ? "host.docker.internal" : redisHost.trim();
  }

  /** Temporal frontend reachable from plugin runner Pods (often host.docker.internal in local K8s). */
  public String resolvedRunnerTemporalHost() {
    return runnerTemporalHost == null || runnerTemporalHost.isBlank()
        ? "host.docker.internal:7233"
        : runnerTemporalHost.trim();
  }
}
