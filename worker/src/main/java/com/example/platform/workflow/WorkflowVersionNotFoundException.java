package com.example.platform.workflow;

public class WorkflowVersionNotFoundException extends RuntimeException {

  public WorkflowVersionNotFoundException(String workflowId, String version) {
    super("Workflow version not found: " + workflowId + "@" + version);
  }
}
