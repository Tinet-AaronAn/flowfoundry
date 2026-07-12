package com.tinet.flowfoundry.security;

import com.tinet.flowfoundry.security.AdminContracts.CreateNamespaceRequest;
import com.tinet.flowfoundry.security.AdminContracts.NamespaceDto;
import com.tinet.flowfoundry.security.AdminContracts.UpdateNamespaceRequest;
import com.tinet.flowfoundry.temporal.TemporalAdminService;
import com.tinet.flowfoundry.temporal.TemporalClusterBootstrapRunner;
import com.tinet.flowfoundry.temporal.TemporalClusterRepository;
import com.tinet.flowfoundry.workflow.WorkflowDefinitionRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NamespaceAdminService {

  private final PlatformNamespaceRepository repository;
  private final WorkflowDefinitionRepository workflowRepository;
  private final PlatformApiKeyRepository apiKeyRepository;
  private final AuditLogService auditLogService;
  private final AdminAccessService adminAccessService;
  private final TemporalClusterRepository temporalClusterRepository;
  private final ObjectProvider<TemporalAdminService> temporalAdminService;

  public NamespaceAdminService(
      PlatformNamespaceRepository repository,
      WorkflowDefinitionRepository workflowRepository,
      PlatformApiKeyRepository apiKeyRepository,
      AuditLogService auditLogService,
      AdminAccessService adminAccessService,
      TemporalClusterRepository temporalClusterRepository,
      ObjectProvider<TemporalAdminService> temporalAdminService) {
    this.repository = repository;
    this.workflowRepository = workflowRepository;
    this.apiKeyRepository = apiKeyRepository;
    this.auditLogService = auditLogService;
    this.adminAccessService = adminAccessService;
    this.temporalClusterRepository = temporalClusterRepository;
    this.temporalAdminService = temporalAdminService;
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
    entity.setTemporalClusterId(resolveTemporalClusterId(request.temporalClusterId()));
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
    TemporalAdminService temporal = temporalAdminService.getIfAvailable();
    if (temporal != null) {
      temporal.registerTemporalNamespaceIfConfigured(namespaceId);
    }
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
    if (request.temporalClusterId() != null) {
      entity.setTemporalClusterId(resolveTemporalClusterId(request.temporalClusterId()));
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
    String runtimeStatus = null;
    TemporalAdminService temporal = temporalAdminService.getIfAvailable();
    if (temporal != null) {
      runtimeStatus = temporal.runtimeStatusForNamespace(entity.getId());
    }
    return new NamespaceDto(
        entity.getId(),
        entity.getDisplayName(),
        entity.getDescription(),
        false,
        entity.getTemporalClusterId() == null
            ? TemporalClusterBootstrapRunner.DEFAULT_CLUSTER_ID
            : entity.getTemporalClusterId(),
        runtimeStatus,
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

  private String resolveTemporalClusterId(String clusterId) {
    String resolved =
        clusterId == null || clusterId.isBlank()
            ? TemporalClusterBootstrapRunner.DEFAULT_CLUSTER_ID
            : clusterId.trim();
    if (!temporalClusterRepository.existsById(resolved)) {
      throw new IllegalArgumentException("Temporal cluster not found: " + resolved);
    }
    return resolved;
  }
}
