package com.tinet.flowfoundary.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "platform_id_registry")
public class PlatformIdRegistryEntity {

  @Id
  @Column(length = 160, nullable = false)
  private String id;

  @Column(nullable = false, length = 32)
  private String kind;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public PlatformIdRegistryEntity() {}

  public PlatformIdRegistryEntity(String id, String kind, Instant createdAt) {
    this.id = id;
    this.kind = kind;
    this.createdAt = createdAt;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
