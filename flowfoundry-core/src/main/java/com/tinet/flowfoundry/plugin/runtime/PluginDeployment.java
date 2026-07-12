package com.tinet.flowfoundry.plugin.runtime;

import java.util.List;

/** Declarative desired state for a plugin Deployment in Kubernetes. */
public record PluginDeployment(
    String pluginId,
    String version,
    String namespace,
    String taskQueue,
    String temporalHost,
    String jarSha256,
    int replicas,
    boolean desiredRunning,
    List<String> scanPackages,
    String memoryRequest,
    String cpuRequest) {

  public String deploymentName() {
    return "ff-plugin-" + pluginId;
  }

  public int effectiveReplicas() {
    return desiredRunning ? replicas : 0;
  }

  public String scanPackagesEnv() {
    return String.join(",", scanPackages);
  }
}
