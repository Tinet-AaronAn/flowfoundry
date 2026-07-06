package com.tinet.flowfoundry.interpreter.model;

import java.util.List;
import java.util.Map;

public record ExecutionNode(
    String id,
    NodeKind kind,
    String activityType,
    String taskQueue,
    String timeout,
    Integer maxAttempts,
    List<String> inputArgs,
    Map<String, String> inputMapping,
    Map<String, String> outputMapping,
    Map<String, Object> config) {

  public NodeKind requiredKind() {
    if (kind == null) {
      throw new IllegalArgumentException("Node kind is required for node " + id);
    }
    return kind;
  }
}
