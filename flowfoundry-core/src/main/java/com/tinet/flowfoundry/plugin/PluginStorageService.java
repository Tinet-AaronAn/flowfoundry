package com.tinet.flowfoundry.plugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/** Stores plugin packages on the local filesystem under {@code ${flowfoundry.plugins.dir}/<id>/<version>/plugin.jar}. */
@Service
public class PluginStorageService {

  private static final String JAR_FILE_NAME = "plugin.jar";

  private final PluginProperties properties;

  public PluginStorageService(PluginProperties properties) {
    this.properties = properties;
  }

  public Path store(String pluginId, String version, byte[] content) {
    Path target = jarPath(pluginId, version);
    try {
      Files.createDirectories(target.getParent());
      Files.write(target, content);
      return target;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to store plugin package: " + target, e);
    }
  }

  public byte[] read(String pluginId, String version) {
    Path source = jarPath(pluginId, version);
    try {
      return Files.readAllBytes(source);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read plugin package: " + source, e);
    }
  }

  public boolean exists(String pluginId, String version) {
    return Files.exists(jarPath(pluginId, version));
  }

  public void delete(String pluginId, String version) {
    Path target = jarPath(pluginId, version);
    try {
      Files.deleteIfExists(target);
      deleteIfEmpty(target.getParent());
      deleteIfEmpty(target.getParent().getParent());
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to delete plugin package: " + target, e);
    }
  }

  public Path jarPath(String pluginId, String version) {
    return Path.of(properties.resolvedDir(), pluginId, version, JAR_FILE_NAME).toAbsolutePath();
  }

  public static String sha256(byte[] content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private static void deleteIfEmpty(Path dir) throws IOException {
    if (dir == null || !Files.isDirectory(dir)) {
      return;
    }
    try (var entries = Files.list(dir)) {
      if (entries.findAny().isEmpty()) {
        Files.deleteIfExists(dir);
      }
    }
  }
}
