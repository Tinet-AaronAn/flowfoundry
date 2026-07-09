package com.tinet.flowfoundry.run;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "flow_run")
public class FlowRunEntity {

  @Id
  @Column(name = "workflow_id", nullable = false, length = 255)
  private String workflowId;

  @Column(name = "temporal_run_id", length = 255)
  private String temporalRunId;

  @Column(nullable = false, length = 64)
  private String namespace;

  @Column(name = "flow_id", nullable = false, length = 128)
  private String flowId;

  @Column(name = "flow_name", length = 255)
  private String flowName;

  @Column(length = 64)
  private String version;

  @Column(name = "business_key", length = 512)
  private String businessKey;

  @Column(name = "run_source", nullable = false, length = 32)
  private String runSource;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(name = "temporal_status", length = 64)
  private String temporalStatus;

  @Column(name = "input_json", columnDefinition = "TEXT")
  private String inputJson;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "last_synced_at")
  private Instant lastSyncedAt;

  @Column(name = "failure_message", columnDefinition = "TEXT")
  private String failureMessage;

  @Column(name = "failure_type", length = 64)
  private String failureType;

  public String getWorkflowId() {
    return workflowId;
  }

  public void setWorkflowId(String workflowId) {
    this.workflowId = workflowId;
  }

  public String getTemporalRunId() {
    return temporalRunId;
  }

  public void setTemporalRunId(String temporalRunId) {
    this.temporalRunId = temporalRunId;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getFlowId() {
    return flowId;
  }

  public void setFlowId(String flowId) {
    this.flowId = flowId;
  }

  public String getFlowName() {
    return flowName;
  }

  public void setFlowName(String flowName) {
    this.flowName = flowName;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getBusinessKey() {
    return businessKey;
  }

  public void setBusinessKey(String businessKey) {
    this.businessKey = businessKey;
  }

  public String getRunSource() {
    return runSource;
  }

  public void setRunSource(String runSource) {
    this.runSource = runSource;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getTemporalStatus() {
    return temporalStatus;
  }

  public void setTemporalStatus(String temporalStatus) {
    this.temporalStatus = temporalStatus;
  }

  public String getInputJson() {
    return inputJson;
  }

  public void setInputJson(String inputJson) {
    this.inputJson = inputJson;
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

  public Instant getLastSyncedAt() {
    return lastSyncedAt;
  }

  public void setLastSyncedAt(Instant lastSyncedAt) {
    this.lastSyncedAt = lastSyncedAt;
  }

  public String getFailureMessage() {
    return failureMessage;
  }

  public void setFailureMessage(String failureMessage) {
    this.failureMessage = failureMessage;
  }

  public String getFailureType() {
    return failureType;
  }

  public void setFailureType(String failureType) {
    this.failureType = failureType;
  }
}
