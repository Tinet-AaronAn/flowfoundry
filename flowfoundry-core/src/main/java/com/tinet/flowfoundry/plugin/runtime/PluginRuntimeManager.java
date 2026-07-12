package com.tinet.flowfoundry.plugin.runtime;

/** SPI for plugin runner orchestration (Kubernetes is the only implementation). */
public interface PluginRuntimeManager {

  void apply(PluginDeployment deployment);

  void delete(PluginDeployment deployment);

  RuntimeStatus probe(PluginDeployment deployment);

  String fetchLogs(PluginDeployment deployment, int tailLines);
}
