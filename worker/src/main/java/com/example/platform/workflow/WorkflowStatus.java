package com.example.platform.workflow;

public enum WorkflowStatus {
  DRAFT,
  ACTIVE,
  RETIRED;

  public static WorkflowStatus fromValue(String value) {
    if (value == null || value.isBlank()) {
      return DRAFT;
    }
    return WorkflowStatus.valueOf(value.trim().toUpperCase());
  }

  public String value() {
    return name().toLowerCase();
  }
}
