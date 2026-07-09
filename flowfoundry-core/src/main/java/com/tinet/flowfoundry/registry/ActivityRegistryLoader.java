package com.tinet.flowfoundry.registry;

import com.tinet.flowfoundry.config.ActivityRegistryProperties;
import com.tinet.flowfoundry.config.FlowFoundryProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class ActivityRegistryLoader {

  private static final Logger log = LoggerFactory.getLogger(ActivityRegistryLoader.class);
  private static final String CORE_REGISTRY = "classpath:core-activities-registry.yaml";

  private final FlowFoundryProperties flowFoundryProperties;
  private final ActivityRegistryProperties legacyProperties;
  private final ResourceLoader resourceLoader;
  private final ObjectMapper yamlMapper;

  public ActivityRegistryLoader(
      FlowFoundryProperties flowFoundryProperties,
      ActivityRegistryProperties legacyProperties,
      ResourceLoader resourceLoader) {
    this.flowFoundryProperties = flowFoundryProperties;
    this.legacyProperties = legacyProperties;
    this.resourceLoader = resourceLoader;
    this.yamlMapper = new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());
  }

  public ActivityRegistry load() {
    ActivityRegistry core = loadCore();
    ActivityRegistry business = loadBusiness();
    ActivityRegistry merged = merge(core, business);
    log.info(
        "Loaded activity registry v{} namespace={} activities={} (core={} business={})",
        merged.version(),
        merged.namespace(),
        merged.activities().size(),
        core == null ? 0 : core.activities().size(),
        business.activities().size());
    return merged;
  }

  public ActivityRegistry loadCore() {
    return loadOptional(CORE_REGISTRY);
  }

  public ActivityRegistry loadBusiness() {
    return loadRequired(resolveRegistryPath());
  }

  private String resolveRegistryPath() {
    String configured = flowFoundryProperties.getActivityRegistry().getPath();
    if (configured != null && !configured.isBlank()) {
      return configured;
    }
    return legacyProperties.path();
  }

  private ActivityRegistry merge(ActivityRegistry core, ActivityRegistry business) {
    List<ActivityRegistry.ActivityDefinition> merged = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    if (core != null) {
      for (ActivityRegistry.ActivityDefinition definition : core.activities()) {
        merged.add(withTaskQueue(definition, core.defaultTaskQueue()));
        seen.add(definition.id());
      }
    }
    for (ActivityRegistry.ActivityDefinition definition : business.activities()) {
      if (seen.add(definition.id())) {
        merged.add(definition);
      }
    }
    List<ActivityRegistry.ActivityGroup> groups = mergeGroups(core, business);
    return new ActivityRegistry(
        business.version(),
        business.namespace(),
        business.defaultTaskQueue(),
        groups,
        merged);
  }

  private List<ActivityRegistry.ActivityGroup> mergeGroups(
      ActivityRegistry core, ActivityRegistry business) {
    List<ActivityRegistry.ActivityGroup> groups = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    if (core != null) {
      for (ActivityRegistry.ActivityGroup group : core.groups()) {
        if (group.id() != null && seen.add(group.id())) {
          groups.add(group);
        }
      }
    }
    for (ActivityRegistry.ActivityGroup group : business.groups()) {
      if (group.id() != null && seen.add(group.id())) {
        groups.add(group);
      }
    }
    return groups;
  }

  private ActivityRegistry.ActivityDefinition withTaskQueue(
      ActivityRegistry.ActivityDefinition definition, String defaultTaskQueue) {
    if (definition.taskQueue() != null && !definition.taskQueue().isBlank()) {
      return definition;
    }
    return new ActivityRegistry.ActivityDefinition(
        definition.id(),
        definition.name(),
        definition.description(),
        definition.group(),
        defaultTaskQueue,
        definition.timeout(),
        definition.retry(),
        definition.idempotency(),
        definition.input(),
        definition.output(),
        definition.cancellable());
  }

  private ActivityRegistry loadRequired(String path) {
    try (InputStream in = open(path)) {
      return yamlMapper.readValue(in, ActivityRegistry.class);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load activity registry from " + path, e);
    }
  }

  private ActivityRegistry loadOptional(String path) {
    try {
      Resource resource = resourceLoader.getResource(path);
      if (!resource.exists()) {
        return null;
      }
      try (InputStream in = resource.getInputStream()) {
        return yamlMapper.readValue(in, ActivityRegistry.class);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load activity registry from " + path, e);
    }
  }

  private InputStream open(String path) throws IOException {
    Resource resource = resourceLoader.getResource(path);
    if (!resource.exists()) {
      throw new IOException("Activity registry not found: " + path);
    }
    return resource.getInputStream();
  }
}
