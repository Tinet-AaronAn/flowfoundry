package com.tinet.flowfoundry.workflow;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workflow_definition")
public class WorkflowDefinitionEntity {

  @Id
  @Column(length = 128, nullable = false)
  private String id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, length = 64)
  private String namespace;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(name = "current_version", length = 32)
  private String currentVersion;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<WorkflowVersionEntity> versions = new ArrayList<>();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getCurrentVersion() {
    return currentVersion;
  }

  public void setCurrentVersion(String currentVersion) {
    this.currentVersion = currentVersion;
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

  public List<WorkflowVersionEntity> getVersions() {
    return versions;
  }

  public void setVersions(List<WorkflowVersionEntity> versions) {
    this.versions = versions;
  }
}
