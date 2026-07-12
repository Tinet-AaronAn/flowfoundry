package com.tinet.flowfoundry.plugin;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import com.tinet.flowfoundry.plugin.PluginContracts.PluginDto;
import com.tinet.flowfoundry.plugin.PluginContracts.PluginPageDto;
import com.tinet.flowfoundry.plugin.PluginContracts.ScalePluginRequest;
import com.tinet.flowfoundry.registry.ActivityCatalogService;
import com.tinet.flowfoundry.registry.ActivityRegistry;
import com.tinet.flowfoundry.security.AdminAccessService;
import com.tinet.flowfoundry.security.AuditActions;
import com.tinet.flowfoundry.security.AuditLogService;
import com.tinet.flowfoundry.plugin.runtime.PluginReconcileService;
import com.tinet.flowfoundry.plugin.runtime.PluginReconcileService.RuntimeView;
import com.tinet.flowfoundry.security.PlatformNamespaceRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plugin package management and runtime lifecycle (upload / start / stop / scale / reconcile).
 * See docs/plugin-runtime-design.md.
 */
@Service
@ConditionalOnFlowFoundryPlatform
public class PluginAdminService {

  private static final Logger log = LoggerFactory.getLogger(PluginAdminService.class);
  private static final Pattern ID_PATTERN = Pattern.compile("[a-z][a-z0-9-]{0,63}");
  private static final Pattern VERSION_PATTERN = Pattern.compile("[0-9A-Za-z][0-9A-Za-z.\\-]{0,31}");
  private static final String RESOURCE_TYPE = "platform_plugin";

  private static final String DESIRED_RUNNING = PluginState.RUNNING.value();
  private static final String DESIRED_STOPPED = PluginState.STOPPED.value();

  private final PlatformPluginRepository pluginRepository;
  private final PlatformNamespaceRepository namespaceRepository;
  private final PluginPackageInspector packageInspector;
  private final PluginStorageService storageService;
  private final ActivityCatalogService activityCatalog;
  private final PluginProperties properties;
  private final PluginReconcileService reconcileService;
  private final AdminAccessService adminAccessService;
  private final AuditLogService auditLogService;

  public PluginAdminService(
      PlatformPluginRepository pluginRepository,
      PlatformNamespaceRepository namespaceRepository,
      PluginPackageInspector packageInspector,
      PluginStorageService storageService,
      ActivityCatalogService activityCatalog,
      PluginProperties properties,
      PluginReconcileService reconcileService,
      AdminAccessService adminAccessService,
      AuditLogService auditLogService) {
    this.pluginRepository = pluginRepository;
    this.namespaceRepository = namespaceRepository;
    this.packageInspector = packageInspector;
    this.storageService = storageService;
    this.activityCatalog = activityCatalog;
    this.properties = properties;
    this.reconcileService = reconcileService;
    this.adminAccessService = adminAccessService;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public PluginDto upload(byte[] jarContent) {
    adminAccessService.requireAdmin();
    if (jarContent == null || jarContent.length == 0) {
      throw new IllegalArgumentException("Plugin package file is empty");
    }
    PluginPackageInspector.PluginPackage pluginPackage = packageInspector.inspect(jarContent);
    PluginDescriptor descriptor = pluginPackage.descriptor();
    ActivityRegistry registry = pluginPackage.registry();
    validate(descriptor, registry);

    String id = descriptor.id().trim();
    String version = descriptor.version().trim();
    if (pluginRepository.existsById(new PlatformPluginKey(id, version))) {
      throw new IllegalArgumentException("Plugin version already exists: " + id + ":" + version);
    }

    Path jarPath = storageService.store(id, version, jarContent);
    Instant now = Instant.now();
    PlatformPluginEntity entity = new PlatformPluginEntity();
    entity.setId(id);
    entity.setVersion(version);
    entity.setDisplayName(
        descriptor.name() == null || descriptor.name().isBlank() ? id : descriptor.name().trim());
    entity.setDescription(trimToNull(descriptor.description()));
    entity.setNamespace(registry.namespace().trim());
    entity.setTaskQueue(registry.defaultTaskQueue().trim());
    entity.setTypedWorkflows(descriptor.typedWorkflows());
    entity.setState(PluginState.READY.value());
    entity.setDesiredState(DESIRED_STOPPED);
    entity.setReplicas(resolveInitialReplicas(id, descriptor));
    entity.setJarPath(jarPath.toString());
    entity.setJarSha256(PluginStorageService.sha256(jarContent));
    entity.setUploadedBy(adminAccessService.actorApiKeyId());
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    PlatformPluginEntity saved = pluginRepository.save(entity);

    activityCatalog.publishPluginRegistry(id, registry);
    audit(
        AuditActions.PLUGIN_UPLOADED,
        saved,
        "sha256=" + saved.getJarSha256() + " activities=" + registry.activities().size());
    log.info(
        "Plugin uploaded id={} version={} namespace={} taskQueue={} activities={}",
        id,
        version,
        saved.getNamespace(),
        saved.getTaskQueue(),
        registry.activities().size());
    return toDto(saved);
  }

  @Transactional(readOnly = true)
  public PluginPageDto list() {
    adminAccessService.requireAdmin();
    List<PluginDto> items =
        pluginRepository.findAll().stream()
            .sorted(
                Comparator.comparing(PlatformPluginEntity::getId)
                    .thenComparing(PlatformPluginEntity::getCreatedAt, Comparator.reverseOrder()))
            .map(this::toDto)
            .toList();
    return new PluginPageDto(items);
  }

  @Transactional(readOnly = true)
  public PluginDto get(String id, String version) {
    adminAccessService.requireAdmin();
    return toDto(requirePlugin(id, version));
  }

  @Transactional
  public PluginDto scale(String id, ScalePluginRequest request) {
    adminAccessService.requireAdmin();
    if (request == null || request.replicas() == null) {
      throw new IllegalArgumentException("replicas is required");
    }
    int replicas = request.replicas();
    int maxReplicas = properties.resolvedMaxReplicas();
    if (replicas < 1 || replicas > maxReplicas) {
      throw new IllegalArgumentException(
          "replicas must be between 1 and " + maxReplicas + ", got: " + replicas);
    }
    List<PlatformPluginEntity> versions = pluginRepository.findByIdOrderByCreatedAtDesc(id);
    if (versions.isEmpty()) {
      throw new IllegalArgumentException("Plugin not found: " + id);
    }
    Instant now = Instant.now();
    for (PlatformPluginEntity entity : versions) {
      entity.setReplicas(replicas);
      entity.setUpdatedAt(now);
    }
    List<PlatformPluginEntity> saved = pluginRepository.saveAll(versions);
    PlatformPluginEntity latest = saved.get(0);
    reconcileService.reconcilePlugin(id);
    audit(AuditActions.PLUGIN_SCALED, latest, "replicas=" + replicas);
    return toDto(latest);
  }

  @Transactional
  public PluginDto start(String id, String version) {
    adminAccessService.requireAdmin();
    requirePlugin(id, version);
    setDesiredVersion(id, version, DESIRED_RUNNING);
    reconcileService.reconcilePlugin(id);
    PlatformPluginEntity entity = requirePlugin(id, version);
    audit(AuditActions.PLUGIN_STARTED, entity, "replicas=" + entity.getReplicas());
    return toDto(entity);
  }

  @Transactional
  public PluginDto stop(String id, String version) {
    adminAccessService.requireAdmin();
    PlatformPluginEntity entity = requirePlugin(id, version);
    entity.setDesiredState(DESIRED_STOPPED);
    entity.setUpdatedAt(Instant.now());
    pluginRepository.save(entity);
    reconcileService.reconcilePlugin(id);
    audit(AuditActions.PLUGIN_STOPPED, entity, null);
    return toDto(requirePlugin(id, version));
  }

  @Transactional
  public PluginDto reload(String id, String version) {
    adminAccessService.requireAdmin();
    requirePlugin(id, version);
    setDesiredVersion(id, version, DESIRED_RUNNING);
    reconcileService.reconcilePlugin(id);
    PlatformPluginEntity entity = requirePlugin(id, version);
    audit(AuditActions.PLUGIN_RELOADED, entity, "replicas=" + entity.getReplicas());
    return toDto(entity);
  }

  @Transactional(readOnly = true)
  public String logs(String id, String version, int tail) {
    adminAccessService.requireAdmin();
    PlatformPluginEntity entity = requirePlugin(id, version);
    int tailLines = Math.min(Math.max(tail, 1), 2000);
    return reconcileService.fetchLogs(entity, tailLines);
  }

  @Transactional
  public void delete(String id, String version) {
    adminAccessService.requireAdmin();
    PlatformPluginEntity entity = requirePlugin(id, version);
    if (PluginState.RUNNING.value().equals(entity.getState())) {
      throw new IllegalArgumentException(
          "Cannot delete a running plugin, stop it first: " + id + ":" + version);
    }
    pluginRepository.delete(entity);
    try {
      storageService.delete(id, version);
    } catch (RuntimeException e) {
      log.warn("Failed to delete plugin package file id={} version={}", id, version, e);
    }
    republishCatalog(id, version);
    audit(AuditActions.PLUGIN_DELETED, entity, null);
  }

  /** Re-publishes the catalog entry for {@code id} from its newest remaining version (or removes it). */
  public void republishCatalog(String id, String deletedVersion) {
    List<PlatformPluginEntity> remaining =
        pluginRepository.findByIdOrderByCreatedAtDesc(id).stream()
            .filter(entity -> !entity.getVersion().equals(deletedVersion))
            .toList();
    if (remaining.isEmpty()) {
      activityCatalog.removePluginRegistry(id);
      return;
    }
    PlatformPluginEntity current = remaining.get(0);
    try {
      byte[] content = storageService.read(current.getId(), current.getVersion());
      ActivityRegistry registry = packageInspector.inspect(content).registry();
      activityCatalog.publishPluginRegistry(id, registry);
    } catch (RuntimeException e) {
      log.warn(
          "Failed to republish plugin registry id={} version={}: {}",
          current.getId(),
          current.getVersion(),
          e.getMessage());
      activityCatalog.removePluginRegistry(id);
    }
  }

  private void validate(PluginDescriptor descriptor, ActivityRegistry registry) {
    if (descriptor.id() == null || !ID_PATTERN.matcher(descriptor.id().trim()).matches()) {
      throw new IllegalArgumentException(
          "Plugin id must match [a-z][a-z0-9-]{0,63}, got: " + descriptor.id());
    }
    if (descriptor.version() == null
        || !VERSION_PATTERN.matcher(descriptor.version().trim()).matches()) {
      throw new IllegalArgumentException("Plugin version is missing or invalid: " + descriptor.version());
    }
    String declaredNamespace = trimToNull(descriptor.declaredNamespace());
    if (declaredNamespace == null) {
      throw new IllegalArgumentException("Plugin descriptor must declare temporal.namespace");
    }
    String registryNamespace = trimToNull(registry.namespace());
    if (!declaredNamespace.equals(registryNamespace)) {
      throw new IllegalArgumentException(
          "Descriptor namespace ("
              + declaredNamespace
              + ") must match activities-registry namespace ("
              + registryNamespace
              + ")");
    }
    if (!namespaceRepository.existsById(declaredNamespace)) {
      throw new IllegalArgumentException(
          "Namespace is not registered on the platform: " + declaredNamespace);
    }
    if (registry.defaultTaskQueue() == null || registry.defaultTaskQueue().isBlank()) {
      throw new IllegalArgumentException("activities-registry must declare defaultTaskQueue");
    }
    if (registry.activities().isEmpty()) {
      throw new IllegalArgumentException("activities-registry must declare at least one activity");
    }
    int replicas = descriptor.defaultReplicas();
    int maxReplicas = properties.resolvedMaxReplicas();
    if (replicas < 1 || replicas > maxReplicas) {
      throw new IllegalArgumentException(
          "runtime.replicas must be between 1 and " + maxReplicas + ", got: " + replicas);
    }
    validateSdkVersion(descriptor.requiredSdkVersion());
    validateActivityConflicts(descriptor.id().trim(), declaredNamespace, registry);
  }

  private void validateActivityConflicts(
      String pluginId, String namespace, ActivityRegistry registry) {
    ActivityRegistry existing = activityCatalog.forNamespaceExcluding(namespace, pluginId);
    Set<String> conflicts = new LinkedHashSet<>();
    for (ActivityRegistry.ActivityDefinition definition : registry.activities()) {
      if (definition.id() == null || definition.id().isBlank()) {
        throw new IllegalArgumentException("activities-registry contains an activity without id");
      }
      if (existing.find(definition.id()).isPresent()) {
        conflicts.add(definition.id());
      }
    }
    if (!conflicts.isEmpty()) {
      throw new IllegalArgumentException(
          "Activity ids already exist in namespace " + namespace + ": " + String.join(", ", conflicts));
    }
  }

  private void validateSdkVersion(String required) {
    if (required == null || required.isBlank()) {
      return;
    }
    String requirement = required.trim();
    if (!requirement.startsWith(">=")) {
      throw new IllegalArgumentException(
          "Unsupported requires.sdkVersion format (only \">=x.y.z\"): " + requirement);
    }
    String minimum = requirement.substring(2).trim();
    String current = ActivityRegistry.class.getPackage().getImplementationVersion();
    if (current == null || current.isBlank()) {
      log.debug("SDK version unknown at runtime, skipping requires.sdkVersion check ({})", requirement);
      return;
    }
    if (compareVersions(current, minimum) < 0) {
      throw new IllegalArgumentException(
          "Plugin requires SDK " + requirement + " but platform provides " + current);
    }
  }

  static int compareVersions(String left, String right) {
    String[] a = left.split("[.-]");
    String[] b = right.split("[.-]");
    int length = Math.max(a.length, b.length);
    for (int i = 0; i < length; i++) {
      int x = i < a.length ? parseIntSafe(a[i]) : 0;
      int y = i < b.length ? parseIntSafe(b[i]) : 0;
      if (x != y) {
        return Integer.compare(x, y);
      }
    }
    return 0;
  }

  private static int parseIntSafe(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private int resolveInitialReplicas(String id, PluginDescriptor descriptor) {
    // Keep the operator-chosen replicas across version uploads; descriptor only seeds the first one.
    return pluginRepository.findByIdOrderByCreatedAtDesc(id).stream()
        .findFirst()
        .map(PlatformPluginEntity::getReplicas)
        .orElse(descriptor.defaultReplicas());
  }

  private void setDesiredVersion(String id, String version, String desiredState) {
    Instant now = Instant.now();
    List<PlatformPluginEntity> versions = pluginRepository.findByIdOrderByCreatedAtDesc(id);
    if (versions.isEmpty()) {
      throw new IllegalArgumentException("Plugin not found: " + id);
    }
    for (PlatformPluginEntity entity : versions) {
      if (entity.getVersion().equals(version)) {
        entity.setDesiredState(desiredState);
      } else if (DESIRED_RUNNING.equals(desiredState)) {
        entity.setDesiredState(DESIRED_STOPPED);
      }
      entity.setUpdatedAt(now);
    }
    pluginRepository.saveAll(versions);
  }

  private PlatformPluginEntity requirePlugin(String id, String version) {
    if (id == null || id.isBlank() || version == null || version.isBlank()) {
      throw new IllegalArgumentException("Plugin id and version are required");
    }
    return pluginRepository
        .findById(new PlatformPluginKey(id.trim(), version.trim()))
        .orElseThrow(() -> new IllegalArgumentException("Plugin not found: " + id + ":" + version));
  }

  private PluginDto toDto(PlatformPluginEntity entity) {
    RuntimeView runtime = reconcileService.runtimeView(entity);
    return new PluginDto(
        entity.getId(),
        entity.getVersion(),
        entity.getDisplayName(),
        entity.getDescription(),
        entity.getNamespace(),
        entity.getTaskQueue(),
        entity.isTypedWorkflows(),
        entity.getState(),
        entity.getDesiredState(),
        entity.getReplicas(),
        entity.getJarSha256(),
        entity.getErrorDetail(),
        entity.getUploadedBy(),
        runtime.readyReplicas(),
        runtime.desiredReplicas(),
        runtime.healthy(),
        runtime.activityPollers(),
        runtime.summary(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  private void audit(String action, PlatformPluginEntity entity, String detail) {
    auditLogService.record(
        new AuditLogService.AuditLogEntry(
            Instant.now(),
            null,
            adminAccessService.actorApiKeyId(),
            action,
            RESOURCE_TYPE,
            entity.getId() + ":" + entity.getVersion(),
            entity.getNamespace(),
            null,
            null,
            null,
            detail,
            null));
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
