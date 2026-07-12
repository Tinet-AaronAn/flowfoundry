package com.tinet.flowfoundry.temporal;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import com.tinet.flowfoundry.config.TemporalProperties;
import com.tinet.flowfoundry.security.PlatformNamespaceRepository;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Resolves {@link TemporalClients} per registered Temporal cluster. Platform namespaces may bind
 * to a specific cluster; unbound namespaces use the default cluster.
 */
@Component
@ConditionalOnFlowFoundryPlatform
public class TemporalConnectionRegistry {

  private final TemporalClusterRepository clusterRepository;
  private final PlatformNamespaceRepository namespaceRepository;
  private final TemporalProperties properties;
  private final Map<String, TemporalClients> clientsByClusterId = new ConcurrentHashMap<>();

  public TemporalConnectionRegistry(
      TemporalClusterRepository clusterRepository,
      PlatformNamespaceRepository namespaceRepository,
      TemporalProperties properties) {
    this.clusterRepository = clusterRepository;
    this.namespaceRepository = namespaceRepository;
    this.properties = properties;
  }

  public TemporalClients clientsForPlatformNamespace(String namespaceId) {
    return clientsForCluster(resolveClusterId(namespaceId));
  }

  public TemporalClients clientsForCluster(String clusterId) {
    String resolved = clusterId == null || clusterId.isBlank() ? defaultClusterId() : clusterId.trim();
    return clientsByClusterId.computeIfAbsent(resolved, this::createClients);
  }

  public TemporalClients defaultClients() {
    return clientsForCluster(defaultClusterId());
  }

  public String resolveClusterId(String namespaceId) {
    if (namespaceId != null && !namespaceId.isBlank()) {
      return namespaceRepository
          .findById(namespaceId.trim())
          .map(entity -> entity.getTemporalClusterId())
          .filter(id -> id != null && !id.isBlank())
          .orElse(defaultClusterId());
    }
    return defaultClusterId();
  }

  public String defaultClusterId() {
    return clusterRepository
        .findByDefaultClusterTrue()
        .map(TemporalClusterEntity::getId)
        .orElse("default");
  }

  public String uiBaseUrlForPlatformNamespace(String namespaceId) {
    String clusterId = resolveClusterId(namespaceId);
    return clusterRepository
        .findById(clusterId)
        .map(TemporalClusterEntity::getUiBaseUrl)
        .filter(url -> url != null && !url.isBlank())
        .map(url -> url.trim().replaceAll("/+$", ""))
        .orElse(properties.resolvedUiBaseUrl());
  }

  public void evictCluster(String clusterId) {
    if (clusterId != null && !clusterId.isBlank()) {
      clientsByClusterId.remove(clusterId.trim());
    }
  }

  public void evictAll() {
    clientsByClusterId.clear();
  }

  private TemporalClients createClients(String clusterId) {
    TemporalClusterEntity cluster =
        clusterRepository
            .findById(clusterId)
            .orElseThrow(
                () -> new IllegalArgumentException("Temporal cluster not found: " + clusterId));
    WorkflowServiceStubs stubs =
        WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder().setTarget(cluster.getHost()).build());
    return new TemporalClients(stubs);
  }
}
