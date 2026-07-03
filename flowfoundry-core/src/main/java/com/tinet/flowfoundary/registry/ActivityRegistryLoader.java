package com.tinet.flowfoundary.registry;

import com.tinet.flowfoundary.config.ActivityRegistryProperties;
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

  private final ActivityRegistryProperties properties;
  private final ResourceLoader resourceLoader;
  private final ObjectMapper yamlMapper;

  public ActivityRegistryLoader(
      ActivityRegistryProperties properties, ResourceLoader resourceLoader) {
    this.properties = properties;
    this.resourceLoader = resourceLoader;
    this.yamlMapper = new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());
  }

  public ActivityRegistry load() {
    ActivityRegistry core = loadOptional(CORE_REGISTRY);
    ActivityRegistry business = loadRequired(properties.path());
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

  private ActivityRegistry merge(ActivityRegistry core, ActivityRegistry business) {
    List<ActivityRegistry.ActivityDefinition> merged = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    if (core != null) {
      for (ActivityRegistry.ActivityDefinition definition : core.activities()) {
        merged.add(definition);
        seen.add(definition.id());
      }
    }
    for (ActivityRegistry.ActivityDefinition definition : business.activities()) {
      if (seen.add(definition.id())) {
        merged.add(definition);
      }
    }
    return new ActivityRegistry(
        business.version(),
        business.namespace(),
        business.defaultTaskQueue(),
        merged);
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
