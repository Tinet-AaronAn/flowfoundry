package com.tinet.flowfoundry.security;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class AdminContracts {

  private AdminContracts() {}

  public record ApiClientDto(
      String id,
      String displayName,
      String description,
      String status,
      boolean admin,
      String keyPrefix,
      Set<String> namespaces,
      Instant createdAt,
      Instant updatedAt,
      Instant lastUsedAt) {}

  public record CreateApiClientRequest(
      String id,
      String displayName,
      String description,
      boolean admin,
      List<String> namespaces) {}

  public record UpdateApiClientRequest(
      String displayName,
      String description,
      String status,
      Boolean admin,
      List<String> namespaces) {}

  public record CreateApiClientResponse(ApiClientDto client, String apiKey) {}

  public record CallerProfileDto(
      String clientId,
      boolean admin,
      Set<String> namespaces,
      Set<String> allowedTenantIds,
      boolean securityEnabled) {}

  public record AuditLogDto(
      Long id,
      Instant occurredAt,
      String clientId,
      String actorClientId,
      String action,
      String resourceType,
      String resourceId,
      String namespace,
      String httpMethod,
      String path,
      Integer statusCode,
      String detail,
      String ipAddress) {}

  public record AuditLogPageDto(
      List<AuditLogDto> items,
      int page,
      int size,
      long totalElements,
      int totalPages) {}
}
