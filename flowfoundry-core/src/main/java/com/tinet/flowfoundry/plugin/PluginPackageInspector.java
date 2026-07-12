package com.tinet.flowfoundry.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tinet.flowfoundry.registry.ActivityRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

/** Extracts plugin descriptor and activity registry from an uploaded plugin jar. */
@Component
public class PluginPackageInspector {

  public static final String DESCRIPTOR_ENTRY = "META-INF/flowfoundry-plugin.yaml";
  public static final String REGISTRY_ENTRY = "activities-registry.yaml";

  private final ObjectMapper yamlMapper =
      new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());

  public record PluginPackage(PluginDescriptor descriptor, ActivityRegistry registry) {}

  public PluginPackage inspect(byte[] jarContent) {
    byte[] descriptorBytes = null;
    byte[] registryBytes = null;
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jarContent))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (DESCRIPTOR_ENTRY.equals(entry.getName())) {
          descriptorBytes = zip.readAllBytes();
        } else if (REGISTRY_ENTRY.equals(entry.getName())) {
          registryBytes = zip.readAllBytes();
        }
        if (descriptorBytes != null && registryBytes != null) {
          break;
        }
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Plugin package is not a readable jar/zip archive", e);
    }
    if (descriptorBytes == null) {
      throw new IllegalArgumentException(
          "Plugin package is missing descriptor: " + DESCRIPTOR_ENTRY);
    }
    if (registryBytes == null) {
      throw new IllegalArgumentException(
          "Plugin package is missing activity registry: " + REGISTRY_ENTRY);
    }
    PluginDescriptor descriptor = parseDescriptor(descriptorBytes);
    ActivityRegistry registry = parseRegistry(registryBytes);
    return new PluginPackage(descriptor, registry);
  }

  private PluginDescriptor parseDescriptor(byte[] content) {
    try {
      PluginDescriptor.Manifest manifest =
          yamlMapper.readValue(content, PluginDescriptor.Manifest.class);
      if (manifest == null || manifest.plugin() == null) {
        throw new IllegalArgumentException(
            "Plugin descriptor must declare a top-level 'plugin:' block");
      }
      return manifest.plugin();
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Failed to parse plugin descriptor " + DESCRIPTOR_ENTRY + ": " + e.getMessage(), e);
    }
  }

  private ActivityRegistry parseRegistry(byte[] content) {
    try {
      return yamlMapper.readValue(content, ActivityRegistry.class);
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Failed to parse activity registry " + REGISTRY_ENTRY + ": " + e.getMessage(), e);
    }
  }
}
