package com.tinet.flowfoundry.run;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "flow_node_run")
@IdClass(FlowNodeRunEntity.FlowNodeRunId.class)
public class FlowNodeRunEntity {

  @Id
  @Column(name = "workflow_id", nullable = false, length = 255)
  private String workflowId;

  @Id
  @Column(name = "node_id", nullable = false, length = 128)
  private String nodeId;

  @Column(name = "node_name", length = 255)
  private String nodeName;

  @Column(name = "node_kind", length = 32)
  private String nodeKind;

  @Column(name = "activity_type", length = 128)
  private String activityType;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "last_detail_json", columnDefinition = "TEXT")
  private String lastDetailJson;

  public String getWorkflowId() {
    return workflowId;
  }

  public void setWorkflowId(String workflowId) {
    this.workflowId = workflowId;
  }

  public String getNodeId() {
    return nodeId;
  }

  public void setNodeId(String nodeId) {
    this.nodeId = nodeId;
  }

  public String getNodeName() {
    return nodeName;
  }

  public void setNodeName(String nodeName) {
    this.nodeName = nodeName;
  }

  public String getNodeKind() {
    return nodeKind;
  }

  public void setNodeKind(String nodeKind) {
    this.nodeKind = nodeKind;
  }

  public String getActivityType() {
    return activityType;
  }

  public void setActivityType(String activityType) {
    this.activityType = activityType;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }

  public String getLastDetailJson() {
    return lastDetailJson;
  }

  public void setLastDetailJson(String lastDetailJson) {
    this.lastDetailJson = lastDetailJson;
  }

  public static final class FlowNodeRunId implements Serializable {
    private String workflowId;
    private String nodeId;

    public FlowNodeRunId() {}

    public FlowNodeRunId(String workflowId, String nodeId) {
      this.workflowId = workflowId;
      this.nodeId = nodeId;
    }

    public String getWorkflowId() {
      return workflowId;
    }

    public void setWorkflowId(String workflowId) {
      this.workflowId = workflowId;
    }

    public String getNodeId() {
      return nodeId;
    }

    public void setNodeId(String nodeId) {
      this.nodeId = nodeId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof FlowNodeRunId that)) {
        return false;
      }
      return Objects.equals(workflowId, that.workflowId) && Objects.equals(nodeId, that.nodeId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(workflowId, nodeId);
    }
  }
}
