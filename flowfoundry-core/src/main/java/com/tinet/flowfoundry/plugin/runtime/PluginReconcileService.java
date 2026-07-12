package com.tinet.flowfoundry.plugin.runtime;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import com.tinet.flowfoundry.plugin.PlatformPluginEntity;
import com.tinet.flowfoundry.plugin.PlatformPluginRepository;
import com.tinet.flowfoundry.plugin.PluginDescriptor;
import com.tinet.flowfoundry.plugin.PluginPackageInspector;
import com.tinet.flowfoundry.plugin.PluginState;
import com.tinet.flowfoundry.plugin.PluginStorageService;
import com.tinet.flowfoundry.security.PlatformNamespaceEntity;
import com.tinet.flowfoundry.security.PlatformNamespaceRepository;
import com.tinet.flowfoundry.temporal.TemporalClusterBootstrapRunner;
import com.tinet.flowfoundry.temporal.TemporalClusterEntity;
import com.tinet.flowfoundry.temporal.TemporalClusterRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reconciles {@code platform_plugin.desired_state} with Kubernetes Deployments and observed health. */
@Service
@ConditionalOnFlowFoundryPlatform
public class PluginReconcileService {

  private static final Logger log = LoggerFactory.getLogger(PluginReconcileService.class);
  private static final String DESIRED_RUNNING = PluginState.RUNNING.value();
  private static final String DESIRED_STOPPED = PluginState.STOPPED.value();

  private final PlatformPluginRepository pluginRepository;
  private final PlatformNamespaceRepository namespaceRepository;
  private final TemporalClusterRepository clusterRepository;
  private final PluginPackageInspector packageInspector;
  private final PluginStorageService storageService;
  private final PluginRuntimeManager runtimeManager;
  private final PluginTemporalProbe temporalProbe;
  private final PluginRuntimeProperties runtimeProperties;

  public PluginReconcileService(
      PlatformPluginRepository pluginRepository,
      PlatformNamespaceRepository namespaceRepository,
      TemporalClusterRepository clusterRepository,
      PluginPackageInspector packageInspector,
      PluginStorageService storageService,
      PluginRuntimeManager runtimeManager,
      PluginTemporalProbe temporalProbe,
      PluginRuntimeProperties runtimeProperties) {
    this.pluginRepository = pluginRepository;
    this.namespaceRepository = namespaceRepository;
    this.clusterRepository = clusterRepository;
    this.packageInspector = packageInspector;
    this.storageService = storageService;
    this.runtimeManager = runtimeManager;
    this.temporalProbe = temporalProbe;
    this.runtimeProperties = runtimeProperties;
  }

  @Transactional
  public void reconcileAll() {
    if (!runtimeProperties.isRuntimeEnabled()) {
      return;
    }
    Map<String, PlatformPluginEntity> desiredRunning = resolveDesiredRunning();
    for (String pluginId : distinctPluginIds()) {
      reconcilePlugin(pluginId, desiredRunning.get(pluginId));
    }
  }

  @Transactional
  public void reconcilePlugin(String pluginId) {
    if (!runtimeProperties.isRuntimeEnabled()) {
      return;
    }
    reconcilePlugin(pluginId, resolveDesiredRunning().get(pluginId));
  }

  public RuntimeView runtimeView(PlatformPluginEntity entity) {
    if (!runtimeProperties.isRuntimeEnabled()) {
      return RuntimeView.disabled();
    }
    boolean desiredRunning = DESIRED_RUNNING.equals(entity.getDesiredState());
    RuntimeStatus status = runtimeManager.probe(toDeployment(entity, desiredRunning));
    int pollers =
        desiredRunning
            ? temporalProbe.activityPollers(entity.getNamespace(), entity.getTaskQueue())
            : 0;
    boolean healthy = !desiredRunning || (status.readyReplicas() >= 1 && pollers > 0);
    String summary =
        status.summary()
            + (desiredRunning ? " activityPollers=" + pollers : "");
    return new RuntimeView(
        status.readyReplicas(),
        desiredRunning ? entity.getReplicas() : 0,
        healthy,
        summary,
        pollers);
  }

  public String fetchLogs(PlatformPluginEntity entity, int tailLines) {
    boolean desiredRunning = DESIRED_RUNNING.equals(entity.getDesiredState());
    return runtimeManager.fetchLogs(toDeployment(entity, desiredRunning), tailLines);
  }

  private void reconcilePlugin(String pluginId, PlatformPluginEntity active) {
    if (active == null) {
      PlatformPluginEntity latest = latestVersion(pluginId).orElse(null);
      if (latest == null) {
        return;
      }
      PluginDeployment stopped = toDeployment(latest, false);
      runtimeManager.apply(stopped);
      markStoppedVersions(pluginId, null);
      return;
    }

    PluginDeployment deployment = toDeployment(active, true);
    runtimeManager.apply(deployment);
    RuntimeStatus status = runtimeManager.probe(deployment);
    int pollers = temporalProbe.activityPollers(active.getNamespace(), active.getTaskQueue());
    updateObservedState(active, status, pollers);
    markStoppedVersions(pluginId, active.getVersion());
    active.setRuntimeRef(deployment.deploymentName());
    active.setUpdatedAt(Instant.now());
    pluginRepository.save(active);
  }

  private void updateObservedState(
      PlatformPluginEntity entity, RuntimeStatus status, int pollers) {
    if (!DESIRED_RUNNING.equals(entity.getDesiredState())) {
      entity.setState(PluginState.STOPPED.value());
      entity.setErrorDetail(null);
      return;
    }
    if (status.readyReplicas() >= 1 && pollers > 0) {
      entity.setState(PluginState.RUNNING.value());
      entity.setErrorDetail(null);
      return;
    }
    if (status.readyReplicas() >= 1) {
      entity.setState(PluginState.RUNNING.value());
      entity.setErrorDetail("Waiting for Temporal activity pollers on " + entity.getTaskQueue());
      return;
    }
    entity.setState(PluginState.READY.value());
    entity.setErrorDetail(status.summary());
  }

  private void markStoppedVersions(String pluginId, String exceptVersion) {
    Instant now = Instant.now();
    for (PlatformPluginEntity entity : pluginRepository.findByIdOrderByCreatedAtDesc(pluginId)) {
      if (exceptVersion != null && exceptVersion.equals(entity.getVersion())) {
        continue;
      }
      if (!DESIRED_STOPPED.equals(entity.getDesiredState())) {
        entity.setDesiredState(DESIRED_STOPPED);
      }
      if (PluginState.RUNNING.value().equals(entity.getState())) {
        entity.setState(PluginState.STOPPED.value());
      }
      entity.setUpdatedAt(now);
      pluginRepository.save(entity);
    }
  }

  private Map<String, PlatformPluginEntity> resolveDesiredRunning() {
    Map<String, PlatformPluginEntity> result = new LinkedHashMap<>();
    pluginRepository.findByDesiredState(DESIRED_RUNNING).stream()
        .sorted(Comparator.comparing(PlatformPluginEntity::getUpdatedAt).reversed())
        .forEach(entity -> result.putIfAbsent(entity.getId(), entity));
    return result;
  }

  private List<String> distinctPluginIds() {
    return pluginRepository.findAll().stream().map(PlatformPluginEntity::getId).distinct().toList();
  }

  private Optional<PlatformPluginEntity> latestVersion(String pluginId) {
    return pluginRepository.findByIdOrderByCreatedAtDesc(pluginId).stream().findFirst();
  }

  private PluginDeployment toDeployment(PlatformPluginEntity entity, boolean desiredRunning) {
    PluginDescriptor descriptor = readDescriptor(entity);
    String temporalHost = resolveTemporalHost(entity.getNamespace());
    return new PluginDeployment(
        entity.getId(),
        entity.getVersion(),
        entity.getNamespace(),
        entity.getTaskQueue(),
        temporalHost,
        entity.getJarSha256(),
        entity.getReplicas(),
        desiredRunning,
        descriptor.basePackages(),
        memoryRequest(descriptor),
        cpuRequest(descriptor));
  }

  private PluginDescriptor readDescriptor(PlatformPluginEntity entity) {
    byte[] content = storageService.read(entity.getId(), entity.getVersion());
    return packageInspector.inspect(content).descriptor();
  }

  private String resolveTemporalHost(String namespace) {
    String clusterHost =
        namespaceRepository
            .findById(namespace)
            .map(PlatformNamespaceEntity::getTemporalClusterId)
            .filter(id -> id != null && !id.isBlank())
            .flatMap(clusterRepository::findById)
            .map(TemporalClusterEntity::getHost)
            .orElseGet(
                () ->
                    clusterRepository
                        .findById(TemporalClusterBootstrapRunner.DEFAULT_CLUSTER_ID)
                        .map(TemporalClusterEntity::getHost)
                        .orElse(null));
    if (clusterHost == null
        || clusterHost.isBlank()
        || clusterHost.startsWith("127.0.0.1")
        || clusterHost.startsWith("localhost")) {
      return runtimeProperties.resolvedRunnerTemporalHost();
    }
    return clusterHost.trim();
  }

  private static String memoryRequest(PluginDescriptor descriptor) {
    if (descriptor.runtime() != null
        && descriptor.runtime().resources() != null
        && descriptor.runtime().resources().memory() != null
        && !descriptor.runtime().resources().memory().isBlank()) {
      return descriptor.runtime().resources().memory().trim();
    }
    return "512Mi";
  }

  private static String cpuRequest(PluginDescriptor descriptor) {
    if (descriptor.runtime() != null
        && descriptor.runtime().resources() != null
        && descriptor.runtime().resources().cpu() != null
        && !descriptor.runtime().resources().cpu().isBlank()) {
      return descriptor.runtime().resources().cpu().trim();
    }
    return "250m";
  }

  public record RuntimeView(
      int readyReplicas, int desiredReplicas, boolean healthy, String summary, int activityPollers) {

    public static RuntimeView disabled() {
      return new RuntimeView(0, 0, false, "runtime disabled", 0);
    }
  }
}
