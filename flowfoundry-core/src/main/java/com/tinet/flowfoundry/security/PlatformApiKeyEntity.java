package com.tinet.flowfoundry.security;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "platform_api_key")
public class PlatformApiKeyEntity {

  @Id
  @Column(length = 64, nullable = false)
  private String id;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(length = 1024)
  private String description;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(name = "admin_flag", nullable = false)
  private boolean admin;

  @Column(name = "key_hash", nullable = false, length = 64)
  private String keyHash;

  @Column(name = "key_prefix", nullable = false, length = 20)
  private String keyPrefix;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "platform_api_key_namespace",
      joinColumns = @JoinColumn(name = "api_key_id"))
  @Column(name = "namespace", nullable = false, length = 64)
  private Set<String> namespaces = new LinkedHashSet<>();

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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public boolean isAdmin() {
    return admin;
  }

  public void setAdmin(boolean admin) {
    this.admin = admin;
  }

  public String getKeyHash() {
    return keyHash;
  }

  public void setKeyHash(String keyHash) {
    this.keyHash = keyHash;
  }

  public String getKeyPrefix() {
    return keyPrefix;
  }

  public void setKeyPrefix(String keyPrefix) {
    this.keyPrefix = keyPrefix;
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

  public Instant getLastUsedAt() {
    return lastUsedAt;
  }

  public void setLastUsedAt(Instant lastUsedAt) {
    this.lastUsedAt = lastUsedAt;
  }

  public Set<String> getNamespaces() {
    return namespaces;
  }

  public void setNamespaces(Set<String> namespaces) {
    this.namespaces = namespaces;
  }
}
