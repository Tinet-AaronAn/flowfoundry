package com.tinet.flowfoundry.plugin.runtime;

/** Observed runtime status from Kubernetes (and optional summary text). */
public record RuntimeStatus(
    boolean exists,
    int readyReplicas,
    int desiredReplicas,
    boolean healthy,
    String summary) {

  public static RuntimeStatus absent() {
    return new RuntimeStatus(false, 0, 0, false, "deployment not found");
  }
}
