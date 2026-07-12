package com.tinet.flowfoundry.plugin;

/** Lifecycle states of a plugin version. P1 only produces READY/FAILED; RUNNING/STOPPED arrive with the runtime (P2). */
public enum PluginState {
  UPLOADED,
  VALIDATED,
  READY,
  RUNNING,
  STOPPED,
  FAILED;

  public String value() {
    return name();
  }
}
