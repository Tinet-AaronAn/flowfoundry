package com.tinet.flowfoundry.security;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class AdminContracts {

  private AdminContracts() {}

  public record ApiKeyDto(
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

  public record CreateApiKeyRequest(
      String id,
      String displayName,
      String description,
      boolean admin,
      List<String> namespaces) {}

  public record UpdateApiKeyRequest(
      String displayName,
      String description,
      String status,
      Boolean admin,
      List<String> namespaces) {}

  public record CreateApiKeyResponse(ApiKeyDto apiKey, String secret) {}

  public record CallerProfileDto(
      String apiKeyId, boolean admin, Set<String> namespaces, boolean securityEnabled) {}

  public record AuditLogDto(
      Long id,
      Instant occurredAt,
      String apiKeyId,
      String actorApiKeyId,
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
