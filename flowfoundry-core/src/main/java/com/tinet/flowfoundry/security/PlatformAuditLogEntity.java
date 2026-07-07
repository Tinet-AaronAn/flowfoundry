package com.tinet.flowfoundry.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "platform_audit_log")
public class PlatformAuditLogEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "client_id", length = 64)
  private String clientId;

  @Column(name = "actor_client_id", length = 64)
  private String actorClientId;

  @Column(nullable = false, length = 64)
  private String action;

  @Column(name = "resource_type", length = 64)
  private String resourceType;

  @Column(name = "resource_id")
  private String resourceId;

  @Column(length = 64)
  private String namespace;

  @Column(name = "http_method", length = 16)
  private String httpMethod;

  @Column(length = 512)
  private String path;

  @Column(name = "status_code")
  private Integer statusCode;

  @Column(columnDefinition = "TEXT")
  private String detail;

  @Column(name = "ip_address", length = 64)
  private String ipAddress;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Instant occurredAt) {
    this.occurredAt = occurredAt;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getActorClientId() {
    return actorClientId;
  }

  public void setActorClientId(String actorClientId) {
    this.actorClientId = actorClientId;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getResourceType() {
    return resourceType;
  }

  public void setResourceType(String resourceType) {
    this.resourceType = resourceType;
  }

  public String getResourceId() {
    return resourceId;
  }

  public void setResourceId(String resourceId) {
    this.resourceId = resourceId;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getHttpMethod() {
    return httpMethod;
  }

  public void setHttpMethod(String httpMethod) {
    this.httpMethod = httpMethod;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public Integer getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(Integer statusCode) {
    this.statusCode = statusCode;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }
}
