package com.example.platform.workflow;

import java.io.Serializable;
import java.util.Objects;

public class WorkflowVersionId implements Serializable {

  private String workflowId;
  private String version;

  public WorkflowVersionId() {}

  public WorkflowVersionId(String workflowId, String version) {
    this.workflowId = workflowId;
    this.version = version;
  }

  public String getWorkflowId() {
    return workflowId;
  }

  public void setWorkflowId(String workflowId) {
    this.workflowId = workflowId;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof WorkflowVersionId that)) {
      return false;
    }
    return Objects.equals(workflowId, that.workflowId) && Objects.equals(version, that.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(workflowId, version);
  }
}
