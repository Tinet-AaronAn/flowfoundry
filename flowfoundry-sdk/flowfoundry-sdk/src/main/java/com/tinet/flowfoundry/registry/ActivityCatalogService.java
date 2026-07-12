package com.tinet.flowfoundry.registry;

import com.tinet.flowfoundry.activity.ActivityTypes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Namespace-scoped view over Activity Registry: global core activities plus business activities
 * registered for the active namespace only. Plugin registries can be published/removed at runtime
 * by the platform plugin manager and contribute to their declared namespace.
 */
public class ActivityCatalogService {

  private final ActivityRegistry coreRegistry;
  private final ActivityRegistry businessRegistry;
  private final Map<String, ActivityRegistry> pluginRegistries = new ConcurrentHashMap<>();

  public ActivityCatalogService(ActivityRegistryLoader loader) {
    ActivityRegistry core = loader.loadCore();
    this.coreRegistry =
        core == null
            ? new ActivityRegistry("1.0", "_core_", ActivityTypes.PLATFORM_TASK_QUEUE, List.of())
            : core;
    this.businessRegistry = loader.loadBusiness();
  }

  public static ActivityCatalogService forRegistries(
      ActivityRegistry coreRegistry, ActivityRegistry businessRegistry) {
    return new ActivityCatalogService(coreRegistry, businessRegistry);
  }

  private ActivityCatalogService(ActivityRegistry coreRegistry, ActivityRegistry businessRegistry) {
    this.coreRegistry =
        coreRegistry == null
            ? new ActivityRegistry("1.0", "_core_", ActivityTypes.PLATFORM_TASK_QUEUE, List.of())
            : coreRegistry;
    this.businessRegistry = businessRegistry;
  }

  /** Business namespace declared by the locally loaded app registry. */
  public String localBusinessNamespace() {
    return businessRegistry.namespace();
  }

  public String localDefaultTaskQueue() {
    return businessRegistry.defaultTaskQueue();
  }

  /** Publishes (or replaces) a plugin-provided registry contributing to its declared namespace. */
  public void publishPluginRegistry(String pluginId, ActivityRegistry registry) {
    pluginRegistries.put(pluginId, registry);
  }

  public void removePluginRegistry(String pluginId) {
    pluginRegistries.remove(pluginId);
  }

  /** Read-only snapshot of published plugin registries keyed by plugin id (for validation). */
  public Map<String, ActivityRegistry> pluginRegistrySnapshot() {
    return Map.copyOf(pluginRegistries);
  }

  /** Core activities (script-runtime, human-task) plus business and plugin activities when {@code namespace} matches. */
  public ActivityRegistry forNamespace(String namespace) {
    return forNamespaceExcluding(namespace, null);
  }

  /** Same as {@link #forNamespace} but ignoring one plugin's registry (used for upgrade conflict checks). */
  public ActivityRegistry forNamespaceExcluding(String namespace, String excludedPluginId) {
    String normalized = namespace == null ? "" : namespace.trim();
    List<ActivityRegistry.ActivityDefinition> merged = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (ActivityRegistry.ActivityDefinition definition : coreRegistry.activities()) {
      if (seen.add(definition.id())) {
        merged.add(definition);
      }
    }
    if (normalized.equals(businessRegistry.namespace())) {
      for (ActivityRegistry.ActivityDefinition definition : businessRegistry.activities()) {
        if (seen.add(definition.id())) {
          merged.add(definition);
        }
      }
    }
    String pluginTaskQueue = null;
    for (ActivityRegistry pluginRegistry : namespacePluginRegistries(normalized, excludedPluginId)) {
      if (pluginTaskQueue == null) {
        pluginTaskQueue = pluginRegistry.defaultTaskQueue();
      }
      for (ActivityRegistry.ActivityDefinition definition : pluginRegistry.activities()) {
        if (seen.add(definition.id())) {
          merged.add(definition);
        }
      }
    }
    List<ActivityRegistry.ActivityGroup> groups = mergeGroups(normalized, excludedPluginId);
    String taskQueue;
    if (normalized.equals(businessRegistry.namespace())) {
      taskQueue = businessRegistry.defaultTaskQueue();
    } else if (pluginTaskQueue != null && !pluginTaskQueue.isBlank()) {
      taskQueue = pluginTaskQueue;
    } else {
      taskQueue = ActivityTypes.PLATFORM_TASK_QUEUE;
    }
    return new ActivityRegistry(
        businessRegistry.version(), normalized, taskQueue, groups, merged);
  }

  private List<ActivityRegistry> namespacePluginRegistries(
      String namespace, String excludedPluginId) {
    return pluginRegistries.entrySet().stream()
        .filter(entry -> !entry.getKey().equals(excludedPluginId))
        .filter(entry -> namespace.equals(entry.getValue().namespace()))
        .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
        .map(Map.Entry::getValue)
        .toList();
  }

  public boolean isAvailable(String namespace, String activityType) {
    if (ActivityTypes.isCore(activityType)) {
      return true;
    }
    return forNamespace(namespace).find(activityType).isPresent();
  }

  private List<ActivityRegistry.ActivityGroup> mergeGroups(
      String namespace, String excludedPluginId) {
    Map<String, ActivityRegistry.ActivityGroup> groups = new LinkedHashMap<>();
    for (ActivityRegistry.ActivityGroup group : coreRegistry.groups()) {
      if (group.id() != null) {
        groups.putIfAbsent(group.id(), group);
      }
    }
    for (ActivityRegistry.ActivityGroup group : businessRegistry.groups()) {
      if (group.id() != null) {
        groups.putIfAbsent(group.id(), group);
      }
    }
    for (ActivityRegistry pluginRegistry : namespacePluginRegistries(namespace, excludedPluginId)) {
      for (ActivityRegistry.ActivityGroup group : pluginRegistry.groups()) {
        if (group.id() != null) {
          groups.putIfAbsent(group.id(), group);
        }
      }
    }
    return List.copyOf(groups.values());
  }
}
