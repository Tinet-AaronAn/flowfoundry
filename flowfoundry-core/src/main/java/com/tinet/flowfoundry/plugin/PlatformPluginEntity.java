package com.tinet.flowfoundry.plugin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "platform_plugin")
@IdClass(PlatformPluginKey.class)
public class PlatformPluginEntity {

  @Id
  @Column(length = 64, nullable = false)
  private String id;

  @Id
  @Column(length = 32, nullable = false)
  private String version;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(length = 1024)
  private String description;

  @Column(nullable = false, length = 64)
  private String namespace;

  @Column(name = "task_queue", nullable = false)
  private String taskQueue;

  @Column(name = "typed_workflows", nullable = false)
  private boolean typedWorkflows;

  @Column(nullable = false, length = 32)
  private String state;

  @Column(name = "desired_state", nullable = false, length = 32)
  private String desiredState;

  @Column(nullable = false)
  private int replicas;

  @Column(name = "jar_path", nullable = false, length = 512)
  private String jarPath;

  @Column(name = "jar_sha256", nullable = false, length = 64)
  private String jarSha256;

  @Column(name = "error_detail")
  private String errorDetail;

  @Column(name = "runtime_ref")
  private String runtimeRef;

  @Column(name = "uploaded_by", nullable = false)
  private String uploadedBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getTaskQueue() {
    return taskQueue;
  }

  public void setTaskQueue(String taskQueue) {
    this.taskQueue = taskQueue;
  }

  public boolean isTypedWorkflows() {
    return typedWorkflows;
  }

  public void setTypedWorkflows(boolean typedWorkflows) {
    this.typedWorkflows = typedWorkflows;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public String getDesiredState() {
    return desiredState;
  }

  public void setDesiredState(String desiredState) {
    this.desiredState = desiredState;
  }

  public int getReplicas() {
    return replicas;
  }

  public void setReplicas(int replicas) {
    this.replicas = replicas;
  }

  public String getJarPath() {
    return jarPath;
  }

  public void setJarPath(String jarPath) {
    this.jarPath = jarPath;
  }

  public String getJarSha256() {
    return jarSha256;
  }

  public void setJarSha256(String jarSha256) {
    this.jarSha256 = jarSha256;
  }

  public String getErrorDetail() {
    return errorDetail;
  }

  public void setErrorDetail(String errorDetail) {
    this.errorDetail = errorDetail;
  }

  public String getRuntimeRef() {
    return runtimeRef;
  }

  public void setRuntimeRef(String runtimeRef) {
    this.runtimeRef = runtimeRef;
  }

  public String getUploadedBy() {
    return uploadedBy;
  }

  public void setUploadedBy(String uploadedBy) {
    this.uploadedBy = uploadedBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
