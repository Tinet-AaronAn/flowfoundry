package com.tinet.flowfoundry.security;

import com.tinet.flowfoundry.security.AdminContracts.CreateNamespaceRequest;
import com.tinet.flowfoundry.security.AdminContracts.NamespaceDto;
import com.tinet.flowfoundry.security.AdminContracts.UpdateNamespaceRequest;
import com.tinet.flowfoundry.workflow.WorkflowDefinitionRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NamespaceAdminService {

  private final PlatformNamespaceRepository repository;
  private final WorkflowDefinitionRepository workflowRepository;
  private final PlatformApiKeyRepository apiKeyRepository;
  private final AuditLogService auditLogService;
  private final AdminAccessService adminAccessService;

  public NamespaceAdminService(
      PlatformNamespaceRepository repository,
      WorkflowDefinitionRepository workflowRepository,
      PlatformApiKeyRepository apiKeyRepository,
      AuditLogService auditLogService,
      AdminAccessService adminAccessService) {
    this.repository = repository;
    this.workflowRepository = workflowRepository;
    this.apiKeyRepository = apiKeyRepository;
    this.auditLogService = auditLogService;
    this.adminAccessService = adminAccessService;
  }

  @Transactional(readOnly = true)
  public List<NamespaceDto> list() {
    return repository.findAll().stream()
        .sorted((left, right) -> left.getId().compareToIgnoreCase(right.getId()))
        .map(this::toDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public NamespaceDto get(String namespaceId) {
    return toDto(requireNamespace(namespaceId));
  }

  /** 平台已登记的全部逻辑 namespace，供管理员 namespace 选择器与上下文 API 使用。 */
  @Transactional(readOnly = true)
  public List<String> registeredNamespaceIds() {
    return repository.findAllIds();
  }

  @Transactional
  public NamespaceDto create(CreateNamespaceRequest request) {
    adminAccessService.requireAdmin();
    String namespaceId = NamespaceIds.normalize(request.id());
    if (repository.existsById(namespaceId)) {
      throw new IllegalArgumentException("Namespace already exists: " + namespaceId);
    }
    Instant now = Instant.now();
    PlatformNamespaceEntity entity = new PlatformNamespaceEntity();
    entity.setId(namespaceId);
    entity.setDisplayName(requireDisplayName(request.displayName(), namespaceId));
    entity.setDescription(trimToNull(request.description()));
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    PlatformNamespaceEntity saved = repository.save(entity);

    auditLogService.record(
        new AuditLogService.AuditLogEntry(
            now,
            null,
            adminAccessService.actorApiKeyId(),
            AuditActions.NAMESPACE_CREATED,
            "namespace",
            namespaceId,
            namespaceId,
            null,
            null,
            null,
            "displayName=" + saved.getDisplayName(),
            null));
    return toDto(saved);
  }

  @Transactional
  public NamespaceDto update(String namespaceId, UpdateNamespaceRequest request) {
    adminAccessService.requireAdmin();
    String normalized = NamespaceIds.normalize(namespaceId);
    requireMutable(normalized);
    PlatformNamespaceEntity entity = requireNamespace(normalized);
    Instant now = Instant.now();
    if (request.displayName() != null && !request.displayName().isBlank()) {
      entity.setDisplayName(requireDisplayName(request.displayName(), namespaceId));
    }
    if (request.description() != null) {
      entity.setDescription(trimToNull(request.description()));
    }
    entity.setUpdatedAt(now);
    PlatformNamespaceEntity saved = repository.save(entity);

    auditLogService.record(
        new AuditLogService.AuditLogEntry(
            now,
            null,
            adminAccessService.actorApiKeyId(),
            AuditActions.NAMESPACE_UPDATED,
            "namespace",
            namespaceId,
            namespaceId,
            null,
            null,
            null,
            "displayName=" + saved.getDisplayName(),
            null));
    return toDto(saved);
  }

  @Transactional
  public void delete(String namespaceId) {
    adminAccessService.requireAdmin();
    String normalized = NamespaceIds.normalize(namespaceId);
    requireDeletable(normalized);
    repository.deleteById(normalized);

    auditLogService.record(
        new AuditLogService.AuditLogEntry(
            Instant.now(),
            null,
            adminAccessService.actorApiKeyId(),
            AuditActions.NAMESPACE_DELETED,
            "namespace",
            normalized,
            normalized,
            null,
            null,
            null,
            null,
            null));
  }

  @Transactional
  public void ensureRegistered(String namespaceId, String displayName, String description) {
    String normalized = NamespaceIds.normalize(namespaceId);
    if (repository.existsById(normalized)) {
      return;
    }
    Instant now = Instant.now();
    PlatformNamespaceEntity entity = new PlatformNamespaceEntity();
    entity.setId(normalized);
    entity.setDisplayName(displayName == null || displayName.isBlank() ? normalized : displayName.trim());
    entity.setDescription(trimToNull(description));
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    repository.save(entity);
  }

  private void requireDeletable(String namespaceId) {
    requireMutable(namespaceId);
    if (!repository.existsById(namespaceId)) {
      throw new IllegalArgumentException("Namespace not found: " + namespaceId);
    }
    long workflowCount = workflowRepository.countByNamespace(namespaceId);
    if (workflowCount > 0) {
      throw new IllegalArgumentException(
          "Namespace still has workflows (" + workflowCount + "): " + namespaceId);
    }
    long apiKeyCount = apiKeyRepository.countByNamespace(namespaceId);
    if (apiKeyCount > 0) {
      throw new IllegalArgumentException(
          "Namespace is referenced by API keys (" + apiKeyCount + "): " + namespaceId);
    }
  }

  private void requireMutable(String namespaceId) {
    // All registered namespaces are user-managed under the unified namespace model.
  }

  private PlatformNamespaceEntity requireNamespace(String namespaceId) {
    return repository
        .findById(NamespaceIds.normalize(namespaceId))
        .orElseThrow(() -> new IllegalArgumentException("Namespace not found: " + namespaceId));
  }

  private NamespaceDto toDto(PlatformNamespaceEntity entity) {
    return new NamespaceDto(
        entity.getId(),
        entity.getDisplayName(),
        entity.getDescription(),
        false,
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  private static String requireDisplayName(String displayName, String fallback) {
    if (displayName == null || displayName.isBlank()) {
      return fallback;
    }
    return displayName.trim();
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
