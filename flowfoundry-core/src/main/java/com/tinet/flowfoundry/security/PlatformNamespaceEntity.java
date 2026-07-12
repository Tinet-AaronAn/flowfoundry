package com.tinet.flowfoundry.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "platform_namespace")
public class PlatformNamespaceEntity {

  @Id
  @Column(length = 64, nullable = false)
  private String id;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(length = 1024)
  private String description;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "temporal_cluster_id", length = 64)
  private String temporalClusterId;

  public String getTemporalClusterId() {
    return temporalClusterId;
  }

  public void setTemporalClusterId(String temporalClusterId) {
    this.temporalClusterId = temporalClusterId;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
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
