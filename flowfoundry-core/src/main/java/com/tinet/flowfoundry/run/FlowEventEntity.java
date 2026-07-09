package com.tinet.flowfoundry.run;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "flow_event")
public class FlowEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "workflow_id", nullable = false, length = 255)
  private String workflowId;

  @Column(name = "sequence_no", nullable = false)
  private Integer sequenceNo;

  @Column(name = "event_type", nullable = false, length = 64)
  private String eventType;

  @Column(name = "node_id", length = 128)
  private String nodeId;

  @Column(name = "node_name", length = 255)
  private String nodeName;

  @Column(name = "node_kind", length = 32)
  private String nodeKind;

  @Column(name = "activity_type", length = 128)
  private String activityType;

  @Column(length = 32)
  private String status;

  @Column(name = "detail_json", columnDefinition = "TEXT")
  private String detailJson;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getWorkflowId() {
    return workflowId;
  }

  public void setWorkflowId(String workflowId) {
    this.workflowId = workflowId;
  }

  public Integer getSequenceNo() {
    return sequenceNo;
  }

  public void setSequenceNo(Integer sequenceNo) {
    this.sequenceNo = sequenceNo;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
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

  public String getDetailJson() {
    return detailJson;
  }

  public void setDetailJson(String detailJson) {
    this.detailJson = detailJson;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Instant occurredAt) {
    this.occurredAt = occurredAt;
  }
}
