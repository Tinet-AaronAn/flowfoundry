package com.tinet.flowfoundry.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tinet.flowfoundry.plugin.PluginContracts.PluginDto;
import com.tinet.flowfoundry.plugin.PluginContracts.ScalePluginRequest;
import com.tinet.flowfoundry.plugin.runtime.PluginReconcileService;
import com.tinet.flowfoundry.plugin.runtime.PluginReconcileService.RuntimeView;
import com.tinet.flowfoundry.registry.ActivityCatalogService;
import com.tinet.flowfoundry.registry.ActivityRegistry;
import com.tinet.flowfoundry.security.AdminAccessService;
import com.tinet.flowfoundry.security.AuditLogService;
import com.tinet.flowfoundry.security.PlatformNamespaceEntity;
import com.tinet.flowfoundry.security.PlatformNamespaceRepository;
import com.tinet.flowfoundry.security.PlatformSecurityProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({
  PluginAdminServiceTest.TestConfig.class,
  PluginAdminService.class,
  PluginPackageInspector.class,
  AdminAccessService.class,
  PlatformSecurityProperties.class,
  AuditLogService.class
})
class PluginAdminServiceTest {

  private static final String NAMESPACE = "plugin-test-ns";

  @Autowired private PluginAdminService pluginAdminService;
  @Autowired private PlatformPluginRepository pluginRepository;
  @Autowired private PlatformNamespaceRepository namespaceRepository;
  @Autowired private ActivityCatalogService activityCatalog;
  @Autowired private PluginStorageService storageService;

  @TestConfiguration
  static class TestConfig {

    @Bean
    PluginProperties pluginProperties() {
      try {
        return new PluginProperties(
            Files.createTempDirectory("flowfoundry-plugins-test").toString(), 10);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Bean
    PluginStorageService pluginStorageService(PluginProperties properties) {
      return new PluginStorageService(properties);
    }

    @Bean
    ActivityCatalogService activityCatalogService() {
      return ActivityCatalogService.forRegistries(
          null, new ActivityRegistry("1.0", "biz-ns", "biz-tq", List.of()));
    }

    @Bean
    PluginReconcileService pluginReconcileService() {
      return new PluginReconcileService(
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null) {
        @Override
        public void reconcileAll() {}

        @Override
        public void reconcilePlugin(String pluginId) {}

        @Override
        public RuntimeView runtimeView(PlatformPluginEntity entity) {
          return RuntimeView.disabled();
        }

        @Override
        public String fetchLogs(PlatformPluginEntity entity, int tailLines) {
          return "";
        }
      };
    }
  }

  @BeforeEach
  void seedNamespace() {
    if (namespaceRepository.existsById(NAMESPACE)) {
      return;
    }
    PlatformNamespaceEntity namespace = new PlatformNamespaceEntity();
    namespace.setId(NAMESPACE);
    namespace.setDisplayName("Plugin Test NS");
    namespace.setCreatedAt(Instant.now());
    namespace.setUpdatedAt(Instant.now());
    namespaceRepository.save(namespace);
  }

  @Test
  void uploadsPluginAndPublishesRegistry() {
    PluginDto dto =
        pluginAdminService.upload(pluginJar("demo-plugin", "1.0.0", NAMESPACE, 3, "demo.hello"));

    assertThat(dto.id()).isEqualTo("demo-plugin");
    assertThat(dto.version()).isEqualTo("1.0.0");
    assertThat(dto.state()).isEqualTo("READY");
    assertThat(dto.desiredState()).isEqualTo("STOPPED");
    assertThat(dto.replicas()).isEqualTo(3);
    assertThat(dto.namespace()).isEqualTo(NAMESPACE);
    assertThat(dto.taskQueue()).isEqualTo("demo-plugin-tq");
    assertThat(dto.jarSha256()).hasSize(64);
    assertThat(storageService.exists("demo-plugin", "1.0.0")).isTrue();

    ActivityRegistry catalog = activityCatalog.forNamespace(NAMESPACE);
    assertThat(catalog.find("demo.hello")).isPresent();
    assertThat(catalog.defaultTaskQueue()).isEqualTo("demo-plugin-tq");
  }

  @Test
  void rejectsUnknownNamespace() {
    assertThatThrownBy(
            () ->
                pluginAdminService.upload(
                    pluginJar("ghost-plugin", "1.0.0", "no-such-ns", 1, "ghost.act")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not registered");
  }

  @Test
  void rejectsDuplicateVersion() {
    pluginAdminService.upload(pluginJar("dup-plugin", "1.0.0", NAMESPACE, 1, "dup.a"));
    assertThatThrownBy(
            () -> pluginAdminService.upload(pluginJar("dup-plugin", "1.0.0", NAMESPACE, 1, "dup.a")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("already exists");
  }

  @Test
  void rejectsActivityConflictBetweenPlugins() {
    pluginAdminService.upload(pluginJar("first-plugin", "1.0.0", NAMESPACE, 1, "shared.act"));
    assertThatThrownBy(
            () ->
                pluginAdminService.upload(
                    pluginJar("second-plugin", "1.0.0", NAMESPACE, 1, "shared.act")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("shared.act");
  }

  @Test
  void allowsUpgradeWithSameActivityIdsAndKeepsReplicas() {
    pluginAdminService.upload(pluginJar("upg-plugin", "1.0.0", NAMESPACE, 2, "upg.act"));
    pluginAdminService.scale("upg-plugin", new ScalePluginRequest(5));

    PluginDto upgraded =
        pluginAdminService.upload(pluginJar("upg-plugin", "1.1.0", NAMESPACE, 2, "upg.act"));

    assertThat(upgraded.version()).isEqualTo("1.1.0");
    assertThat(upgraded.replicas()).isEqualTo(5);
    assertThat(pluginRepository.findByIdOrderByCreatedAtDesc("upg-plugin")).hasSize(2);
  }

  @Test
  void scaleValidatesBounds() {
    pluginAdminService.upload(pluginJar("scale-plugin", "1.0.0", NAMESPACE, 1, "scale.act"));

    PluginDto scaled = pluginAdminService.scale("scale-plugin", new ScalePluginRequest(4));
    assertThat(scaled.replicas()).isEqualTo(4);

    assertThatThrownBy(() -> pluginAdminService.scale("scale-plugin", new ScalePluginRequest(0)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> pluginAdminService.scale("scale-plugin", new ScalePluginRequest(11)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 1 and 10");
    assertThatThrownBy(() -> pluginAdminService.scale("missing", new ScalePluginRequest(2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void deleteRemovesVersionAndRepublishesRemaining() {
    pluginAdminService.upload(pluginJar("del-plugin", "1.0.0", NAMESPACE, 1, "del.act"));
    pluginAdminService.upload(pluginJar("del-plugin", "1.1.0", NAMESPACE, 1, "del.act"));

    pluginAdminService.delete("del-plugin", "1.1.0");
    assertThat(activityCatalog.forNamespace(NAMESPACE).find("del.act")).isPresent();
    assertThat(storageService.exists("del-plugin", "1.1.0")).isFalse();

    pluginAdminService.delete("del-plugin", "1.0.0");
    assertThat(activityCatalog.forNamespace(NAMESPACE).find("del.act")).isEmpty();
    assertThat(pluginRepository.findByIdOrderByCreatedAtDesc("del-plugin")).isEmpty();
  }

  @Test
  void rejectsPackageWithoutDescriptor() {
    byte[] jar = zip(entry("activities-registry.yaml", registryYaml("x-plugin", NAMESPACE, "x.a")));
    assertThatThrownBy(() -> pluginAdminService.upload(jar))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("flowfoundry-plugin.yaml");
  }

  @Test
  void rejectsNamespaceMismatchBetweenDescriptorAndRegistry() {
    byte[] jar =
        zip(
            entry(
                "META-INF/flowfoundry-plugin.yaml",
                descriptorYaml("mm-plugin", "1.0.0", "other-ns", 1)),
            entry("activities-registry.yaml", registryYaml("mm-plugin", NAMESPACE, "mm.act")));
    assertThatThrownBy(() -> pluginAdminService.upload(jar))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must match");
  }

  private static byte[] pluginJar(
      String id, String version, String namespace, int replicas, String... activityIds) {
    return zip(
        entry("META-INF/flowfoundry-plugin.yaml", descriptorYaml(id, version, namespace, replicas)),
        entry("activities-registry.yaml", registryYaml(id, namespace, activityIds)));
  }

  private static String descriptorYaml(String id, String version, String namespace, int replicas) {
    return """
        plugin:
          id: %s
          version: %s
          name: Test Plugin %s
          description: test plugin package
          basePackages:
            - com.example.test
          temporal:
            namespace: %s
          capabilities:
            typedWorkflows: false
          runtime:
            replicas: %d
        """
        .formatted(id, version, id, namespace, replicas);
  }

  private static String registryYaml(String id, String namespace, String... activityIds) {
    StringBuilder yaml =
        new StringBuilder(
            """
            version: "1.0"
            namespace: %s
            defaultTaskQueue: %s-tq
            activities:
            """
                .formatted(namespace, id));
    for (String activityId : activityIds) {
      yaml.append(
          """
            - id: %s
              name: %s
              timeout: 30s
          """
              .formatted(activityId, activityId));
    }
    return yaml.toString();
  }

  private record ZipFileEntry(String name, String content) {}

  private static ZipFileEntry entry(String name, String content) {
    return new ZipFileEntry(name, content);
  }

  private static byte[] zip(ZipFileEntry... entries) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      for (ZipFileEntry fileEntry : entries) {
        zip.putNextEntry(new ZipEntry(fileEntry.name()));
        zip.write(fileEntry.content().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }
}
