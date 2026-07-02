package com.example.platform.workflow;

public class WorkflowConflictException extends RuntimeException {

  public WorkflowConflictException(String message) {
    super(message);
  }
}
