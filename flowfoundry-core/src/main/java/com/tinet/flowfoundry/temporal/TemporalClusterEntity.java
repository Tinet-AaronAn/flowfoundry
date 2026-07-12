package com.tinet.flowfoundry.temporal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "temporal_cluster")
public class TemporalClusterEntity {

  @Id
  @Column(length = 64, nullable = false)
  private String id;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(nullable = false)
  private String host;

  @Column(name = "ui_base_url")
  private String uiBaseUrl;

  @Column(name = "is_default", nullable = false)
  private boolean defaultCluster;

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

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public String getUiBaseUrl() {
    return uiBaseUrl;
  }

  public void setUiBaseUrl(String uiBaseUrl) {
    this.uiBaseUrl = uiBaseUrl;
  }

  public boolean isDefaultCluster() {
    return defaultCluster;
  }

  public void setDefaultCluster(boolean defaultCluster) {
    this.defaultCluster = defaultCluster;
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
