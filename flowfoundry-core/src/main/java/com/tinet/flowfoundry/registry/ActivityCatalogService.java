package com.tinet.flowfoundry.registry;

import com.tinet.flowfoundry.activity.ActivityTypes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Namespace-scoped view over Activity Registry: global core activities plus business activities
 * registered for the active namespace only.
 */
public class ActivityCatalogService {

  private final ActivityRegistry coreRegistry;
  private final ActivityRegistry businessRegistry;

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

  /** Core activities (script-runtime, human-task) plus business activities when {@code namespace} matches. */
  public ActivityRegistry forNamespace(String namespace) {
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
    List<ActivityRegistry.ActivityGroup> groups = mergeGroups();
    String taskQueue =
        normalized.equals(businessRegistry.namespace())
            ? businessRegistry.defaultTaskQueue()
            : ActivityTypes.PLATFORM_TASK_QUEUE;
    return new ActivityRegistry(
        businessRegistry.version(), normalized, taskQueue, groups, merged);
  }

  public boolean isAvailable(String namespace, String activityType) {
    if (ActivityTypes.isCore(activityType)) {
      return true;
    }
    return forNamespace(namespace).find(activityType).isPresent();
  }

  private List<ActivityRegistry.ActivityGroup> mergeGroups() {
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
    return List.copyOf(groups.values());
  }
}
