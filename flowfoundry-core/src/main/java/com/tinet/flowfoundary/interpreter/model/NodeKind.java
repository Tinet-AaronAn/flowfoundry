package com.example.platform.interpreter.model;

public enum NodeKind {
  START,
  END,
  ACTIVITY,
  DECISION,
  HUMAN_TASK,
  TIMER,
  SCRIPT_TASK,
  CHILD_WORKFLOW;

  public static NodeKind from(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("Node kind is required");
    }
    return NodeKind.valueOf(raw.trim().replace('-', '_').toUpperCase());
  }
}
