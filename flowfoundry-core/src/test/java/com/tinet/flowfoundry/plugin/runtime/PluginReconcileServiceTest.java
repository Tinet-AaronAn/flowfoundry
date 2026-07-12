package com.tinet.flowfoundry.plugin.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.tinet.flowfoundry.plugin.PlatformPluginEntity;
import com.tinet.flowfoundry.plugin.PlatformPluginKey;
import com.tinet.flowfoundry.plugin.PlatformPluginRepository;
import com.tinet.flowfoundry.plugin.PluginPackageInspector;
import com.tinet.flowfoundry.plugin.PluginProperties;
import com.tinet.flowfoundry.plugin.PluginState;
import com.tinet.flowfoundry.plugin.PluginStorageService;
import com.tinet.flowfoundry.security.PlatformNamespaceEntity;
import com.tinet.flowfoundry.security.PlatformNamespaceRepository;
import com.tinet.flowfoundry.temporal.TemporalClusterRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
  PluginReconcileServiceTest.TestConfig.class,
  PluginReconcileService.class,
  PluginPackageInspector.class
})
class PluginReconcileServiceTest {

  private static final String PLUGIN_ID = "demo-plugin";
  private static final String VERSION = "1.0.0";
  private static final String NAMESPACE = "demo-ns";

  @Autowired private PluginReconcileService reconcileService;
  @Autowired private PlatformPluginRepository pluginRepository;
  @Autowired private PlatformNamespaceRepository namespaceRepository;
  @Autowired private TemporalClusterRepository clusterRepository;
  @Autowired private PluginStorageService storageService;
  @Autowired private RecordingRuntimeManager runtimeManager;
  @Autowired private AtomicInteger temporalPollers;

  @BeforeEach
  void seed() {
    runtimeManager.applied.clear();
    runtimeManager.nextProbe = RuntimeStatus.absent();
    temporalPollers.set(0);
    if (!namespaceRepository.existsById(NAMESPACE)) {
      PlatformNamespaceEntity namespace = new PlatformNamespaceEntity();
      namespace.setId(NAMESPACE);
      namespace.setDisplayName("Demo NS");
      namespace.setCreatedAt(Instant.now());
      namespace.setUpdatedAt(Instant.now());
      namespaceRepository.save(namespace);
    }
    storageService.store(PLUGIN_ID, VERSION, pluginJar(PLUGIN_ID, VERSION, NAMESPACE, 2, "demo.act"));
  }

  @Test
  void reconcileRunningPluginAppliesDeploymentAndMarksRunningWhenHealthy() {
    PlatformPluginEntity entity = runningEntity();
    pluginRepository.save(entity);
    runtimeManager.nextProbe = new RuntimeStatus(true, 1, 1, true, "1/1 ready");
    temporalPollers.set(2);

    reconcileService.reconcileAll();

    PlatformPluginEntity updated =
        pluginRepository.findById(new PlatformPluginKey(PLUGIN_ID, VERSION)).orElseThrow();
    assertThat(runtimeManager.applied).hasSize(1);
    PluginDeployment deployment = runtimeManager.applied.get(0);
    assertThat(deployment.pluginId()).isEqualTo(PLUGIN_ID);
    assertThat(deployment.desiredRunning()).isTrue();
    assertThat(deployment.effectiveReplicas()).isEqualTo(2);
    assertThat(updated.getState()).isEqualTo(PluginState.RUNNING.value());
    assertThat(updated.getErrorDetail()).isNull();
    assertThat(updated.getRuntimeRef()).isEqualTo("ff-plugin-" + PLUGIN_ID);
  }

  @Test
  void reconcileStoppedPluginScalesDeploymentToZero() {
    PlatformPluginEntity entity = runningEntity();
    entity.setDesiredState(PluginState.STOPPED.value());
    pluginRepository.save(entity);

    reconcileService.reconcileAll();

    assertThat(runtimeManager.applied).hasSize(1);
    assertThat(runtimeManager.applied.get(0).effectiveReplicas()).isZero();
  }

  @Test
  void runtimeViewDisabledWhenRuntimeOff() {
    PluginReconcileService disabledService =
        new PluginReconcileService(
            pluginRepository,
            namespaceRepository,
            clusterRepository,
            new PluginPackageInspector(),
            storageService,
            runtimeManager,
            new PluginTemporalProbe(null, namespaceRepository) {
              @Override
              public int activityPollers(String namespace, String taskQueue) {
                return 0;
              }
            },
            new PluginRuntimeProperties(
                false,
                "flowfoundry-plugins",
                "flowfoundry-plugin-runner:local",
                "http://host.docker.internal:8081",
                "secret",
                15000,
                60,
                "host.docker.internal",
                null));

    PluginReconcileService.RuntimeView view =
        disabledService.runtimeView(runningEntity());

    assertThat(view.healthy()).isFalse();
    assertThat(view.summary()).contains("disabled");
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    AtomicInteger temporalPollers() {
      return new AtomicInteger();
    }

    @Bean
    PluginProperties pluginProperties() {
      try {
        return new PluginProperties(
            Files.createTempDirectory("flowfoundry-reconcile-test").toString(), 10);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Bean
    PluginStorageService pluginStorageService(PluginProperties properties) {
      return new PluginStorageService(properties);
    }

    @Bean
    RecordingRuntimeManager recordingRuntimeManager() {
      return new RecordingRuntimeManager();
    }

    @Bean
    PluginTemporalProbe pluginTemporalProbe(
        PlatformNamespaceRepository namespaceRepository, AtomicInteger pollers) {
      return new PluginTemporalProbe(null, namespaceRepository) {
        @Override
        public int activityPollers(String namespace, String taskQueue) {
          return pollers.get();
        }
      };
    }

    @Bean
    PluginRuntimeProperties pluginRuntimeProperties() {
      return new PluginRuntimeProperties(
          true,
          "flowfoundry-plugins",
          "flowfoundry-plugin-runner:local",
          "http://host.docker.internal:8081",
          "secret",
          15000,
          60,
          "host.docker.internal",
          null);
    }
  }

  private static PlatformPluginEntity runningEntity() {
    PlatformPluginEntity entity = new PlatformPluginEntity();
    entity.setId(PLUGIN_ID);
    entity.setVersion(VERSION);
    entity.setDisplayName("Demo");
    entity.setNamespace(NAMESPACE);
    entity.setTaskQueue("demo-plugin-tq");
    entity.setTypedWorkflows(false);
    entity.setState(PluginState.READY.value());
    entity.setDesiredState(PluginState.RUNNING.value());
    entity.setReplicas(2);
    entity.setJarPath("/tmp/demo.jar");
    entity.setJarSha256("abc");
    entity.setUploadedBy("test");
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());
    return entity;
  }

  private static byte[] pluginJar(
      String id, String version, String namespace, int replicas, String... activityIds) {
    String descriptor =
        """
        plugin:
          id: %s
          version: %s
          name: Test Plugin %s
          basePackages:
            - com.example.test
          temporal:
            namespace: %s
          runtime:
            replicas: %d
        """
            .formatted(id, version, id, namespace, replicas);
    StringBuilder registry =
        new StringBuilder(
            """
            version: "1.0"
            namespace: %s
            defaultTaskQueue: %s-tq
            activities:
            """
                .formatted(namespace, id));
    for (String activityId : activityIds) {
      registry.append(
          """
            - id: %s
              name: %s
              timeout: 30s
          """
              .formatted(activityId, activityId));
    }
    return zip(
        new ZipFileEntry("META-INF/flowfoundry-plugin.yaml", descriptor),
        new ZipFileEntry("activities-registry.yaml", registry.toString()));
  }

  private record ZipFileEntry(String name, String content) {}

  private static byte[] zip(ZipFileEntry... entries) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      for (ZipFileEntry fileEntry : entries) {
        zip.putNextEntry(new ZipEntry(fileEntry.name()));
        zip.write(fileEntry.content().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return out.toByteArray();
  }

  static final class RecordingRuntimeManager implements PluginRuntimeManager {

    final List<PluginDeployment> applied = new ArrayList<>();
    RuntimeStatus nextProbe = RuntimeStatus.absent();

    @Override
    public void apply(PluginDeployment deployment) {
      applied.add(deployment);
    }

    @Override
    public void delete(PluginDeployment deployment) {}

    @Override
    public RuntimeStatus probe(PluginDeployment deployment) {
      return nextProbe;
    }

    @Override
    public String fetchLogs(PluginDeployment deployment, int tailLines) {
      return "";
    }
  }
}
