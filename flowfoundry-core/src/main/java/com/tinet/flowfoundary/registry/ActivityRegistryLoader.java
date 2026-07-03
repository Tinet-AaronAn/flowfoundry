package com.example.platform.registry;

import com.example.platform.config.ActivityRegistryProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class ActivityRegistryLoader {

  private static final Logger log = LoggerFactory.getLogger(ActivityRegistryLoader.class);

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
    String path = properties.path();
    try (InputStream in = open(path)) {
      ActivityRegistry registry = yamlMapper.readValue(in, ActivityRegistry.class);
      log.info(
          "Loaded activity registry v{} namespace={} activities={}",
          registry.version(),
          registry.namespace(),
          registry.activities().size());
      return registry;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load activity registry from " + path, e);
    }
  }

  private InputStream open(String path) throws IOException {
    if (path.startsWith("classpath:")) {
      Resource resource = resourceLoader.getResource(path);
      return resource.getInputStream();
    }
    return Files.newInputStream(Path.of(path));
  }
}
