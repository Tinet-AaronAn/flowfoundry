package com.tinet.flowfoundary.workflow;

public class WorkflowNotFoundException extends RuntimeException {

  public WorkflowNotFoundException(String workflowId) {
    super("Workflow not found: " + workflowId);
  }
}
