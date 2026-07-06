package com.tinet.flowfoundry.interpreter.model;

public enum NodeKind {
  START,
  END,
  ACTIVITY,
  GATEWAY,
  HUMAN_TASK,
  INTERMEDIATE_EVENT,
  CHILD_WORKFLOW;

  public static NodeKind from(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("Node kind is required");
    }
    String normalized = raw.trim().replace('-', '_').toUpperCase();
    if ("TIMER".equals(normalized)) {
      return INTERMEDIATE_EVENT;
    }
    if ("USERTASK".equals(normalized) || "USER_TASK".equals(normalized)) {
      return HUMAN_TASK;
    }
    return NodeKind.valueOf(normalized);
  }
}
