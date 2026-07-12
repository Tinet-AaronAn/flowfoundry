package com.tinet.flowfoundry.temporal;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import com.tinet.flowfoundry.security.AdminAccessService;
import com.tinet.flowfoundry.security.AuditActions;
import com.tinet.flowfoundry.security.AuditLogService;
import com.tinet.flowfoundry.security.PlatformNamespaceRepository;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.CreateTemporalClusterRequest;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.TemporalClusterDto;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.TemporalClusterPageDto;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.UpdateTemporalClusterRequest;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnFlowFoundryPlatform
public class TemporalClusterAdminService {

  private final TemporalClusterRepository clusterRepository;
  private final PlatformNamespaceRepository namespaceRepository;
  private final TemporalClusterProbe clusterProbe;
  private final TemporalConnectionRegistry connectionRegistry;
  private final AdminAccessService adminAccessService;
  private final AuditLogService auditLogService;

  public TemporalClusterAdminService(
      TemporalClusterRepository clusterRepository,
      PlatformNamespaceRepository namespaceRepository,
      TemporalClusterProbe clusterProbe,
      TemporalConnectionRegistry connectionRegistry,
      AdminAccessService adminAccessService,
      AuditLogService auditLogService) {
    this.clusterRepository = clusterRepository;
    this.namespaceRepository = namespaceRepository;
    this.clusterProbe = clusterProbe;
    this.connectionRegistry = connectionRegistry;
    this.adminAccessService = adminAccessService;
    this.auditLogService = auditLogService;
  }

  @Transactional(readOnly = true)
  public TemporalClusterPageDto list() {
    adminAccessService.requireAdmin();
    List<TemporalClusterDto> items =
        clusterRepository.findAll().stream()
            .sorted(Comparator.comparing(TemporalClusterEntity::getId))
            .map(this::toDto)
            .toList();
    return new TemporalClusterPageDto(items);
  }

  @Transactional(readOnly = true)
  public TemporalClusterDto get(String clusterId) {
    adminAccessService.requireAdmin();
    return toDto(requireCluster(clusterId));
  }

  @Transactional
  public TemporalClusterDto create(CreateTemporalClusterRequest request) {
    adminAccessService.requireAdmin();
    String id = requireId(request.id());
    if (clusterRepository.existsById(id)) {
      throw new IllegalArgumentException("Temporal cluster already exists: " + id);
    }
    Instant now = Instant.now();
    TemporalClusterEntity entity = new TemporalClusterEntity();
    entity.setId(id);
    entity.setDisplayName(requireDisplayName(request.displayName(), id));
    entity.setHost(requireHost(request.host()));
    entity.setUiBaseUrl(trimToNull(request.uiBaseUrl()));
    entity.setDefaultCluster(Boolean.TRUE.equals(request.defaultCluster()));
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    if (entity.isDefaultCluster()) {
      clearDefaultCluster();
    }
    TemporalClusterEntity saved = clusterRepository.save(entity);
    connectionRegistry.evictAll();
    audit(clusterIdAction(AuditActions.TEMPORAL_CLUSTER_CREATED, saved.getId(), saved.getHost()));
    return toDto(saved);
  }

  @Transactional
  public TemporalClusterDto update(String clusterId, UpdateTemporalClusterRequest request) {
    adminAccessService.requireAdmin();
    TemporalClusterEntity entity = requireCluster(clusterId);
    Instant now = Instant.now();
    if (request.displayName() != null && !request.displayName().isBlank()) {
      entity.setDisplayName(requireDisplayName(request.displayName(), clusterId));
    }
    if (request.host() != null && !request.host().isBlank()) {
      entity.setHost(requireHost(request.host()));
    }
    if (request.uiBaseUrl() != null) {
      entity.setUiBaseUrl(trimToNull(request.uiBaseUrl()));
    }
    if (request.defaultCluster() != null) {
      if (request.defaultCluster()) {
        clearDefaultClusterExcept(clusterId);
      }
      entity.setDefaultCluster(request.defaultCluster());
    }
    entity.setUpdatedAt(now);
    TemporalClusterEntity saved = clusterRepository.save(entity);
    connectionRegistry.evictCluster(saved.getId());
    audit(clusterIdAction(AuditActions.TEMPORAL_CLUSTER_UPDATED, saved.getId(), saved.getHost()));
    return toDto(saved);
  }

  @Transactional
  public void delete(String clusterId) {
    adminAccessService.requireAdmin();
    TemporalClusterEntity entity = requireCluster(clusterId);
    if (entity.isDefaultCluster()) {
      throw new IllegalArgumentException("Cannot delete the default Temporal cluster: " + clusterId);
    }
    long boundNamespaces =
        namespaceRepository.findAll().stream()
            .filter(ns -> clusterId.equals(ns.getTemporalClusterId()))
            .count();
    if (boundNamespaces > 0) {
      throw new IllegalArgumentException(
          "Temporal cluster is bound to namespaces (" + boundNamespaces + "): " + clusterId);
    }
    clusterRepository.delete(entity);
    connectionRegistry.evictCluster(clusterId);
    audit(clusterIdAction(AuditActions.TEMPORAL_CLUSTER_DELETED, clusterId, entity.getHost()));
  }

  private TemporalClusterDto toDto(TemporalClusterEntity entity) {
    TemporalClusterProbe.ProbeResult probe = clusterProbe.probe(entity.getId());
    return new TemporalClusterDto(
        entity.getId(),
        entity.getDisplayName(),
        entity.getHost(),
        resolvedUiBaseUrl(entity),
        entity.isDefaultCluster(),
        probe.reachable(),
        probe.serverVersion(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  static String resolvedUiBaseUrl(TemporalClusterEntity entity) {
    if (entity.getUiBaseUrl() == null || entity.getUiBaseUrl().isBlank()) {
      return "http://127.0.0.1:8080";
    }
    return entity.getUiBaseUrl().trim().replaceAll("/+$", "");
  }

  private TemporalClusterEntity requireCluster(String clusterId) {
    if (clusterId == null || clusterId.isBlank()) {
      throw new IllegalArgumentException("Temporal cluster id is required");
    }
    return clusterRepository
        .findById(clusterId.trim())
        .orElseThrow(() -> new IllegalArgumentException("Temporal cluster not found: " + clusterId));
  }

  private void clearDefaultCluster() {
    clearDefaultClusterExcept(null);
  }

  private void clearDefaultClusterExcept(String exceptClusterId) {
    for (TemporalClusterEntity cluster : clusterRepository.findAll()) {
      if (cluster.isDefaultCluster()
          && (exceptClusterId == null || !exceptClusterId.equals(cluster.getId()))) {
        cluster.setDefaultCluster(false);
        cluster.setUpdatedAt(Instant.now());
        clusterRepository.save(cluster);
      }
    }
  }

  private void audit(AuditLogService.AuditLogEntry entry) {
    auditLogService.record(entry);
  }

  private AuditLogService.AuditLogEntry clusterIdAction(String action, String clusterId, String host) {
    return new AuditLogService.AuditLogEntry(
        Instant.now(),
        null,
        adminAccessService.actorApiKeyId(),
        action,
        "temporal_cluster",
        clusterId,
        clusterId,
        null,
        null,
        null,
        "host=" + host,
        null);
  }

  private static String requireId(String id) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("Temporal cluster id is required");
    }
    return id.trim();
  }

  private static String requireDisplayName(String displayName, String fallback) {
    if (displayName == null || displayName.isBlank()) {
      return fallback;
    }
    return displayName.trim();
  }

  private static String requireHost(String host) {
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("Temporal host is required");
    }
    return host.trim();
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
