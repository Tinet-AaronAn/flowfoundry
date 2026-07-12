package com.tinet.flowfoundry.pluginrunner;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime settings for the plugin host process (injected by the platform Deployment). */
@ConfigurationProperties(prefix = "flowfoundry.plugin")
public class PluginRunnerProperties {

  /** Comma-separated base packages to component-scan from the plugin jar. */
  private String scanPackages = "";

  public String getScanPackages() {
    return scanPackages;
  }

  public void setScanPackages(String scanPackages) {
    this.scanPackages = scanPackages == null ? "" : scanPackages;
  }
}
