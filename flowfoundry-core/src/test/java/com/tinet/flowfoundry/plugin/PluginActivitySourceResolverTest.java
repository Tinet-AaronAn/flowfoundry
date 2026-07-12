package com.tinet.flowfoundry.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import com.tinet.flowfoundry.security.PlatformNamespaceEntity;
import com.tinet.flowfoundry.security.PlatformNamespaceRepository;
import com.tinet.flowfoundry.registry.ActivityCatalogService;
import com.tinet.flowfoundry.registry.ActivityRegistry;
import java.time.Instant;
import java.util.List;
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
@Import({PluginActivitySourceResolverTest.TestConfig.class, PluginActivitySourceResolver.class})
class PluginActivitySourceResolverTest {

  private static final String NAMESPACE = "plugin-source-ns";

  @Autowired private PluginActivitySourceResolver resolver;
  @Autowired private ActivityCatalogService activityCatalog;
  @Autowired private PlatformPluginRepository pluginRepository;
  @Autowired private PlatformNamespaceRepository namespaceRepository;

  @TestConfiguration
  static class TestConfig {

    @Bean
    ActivityCatalogService activityCatalogService() {
      return ActivityCatalogService.forRegistries(
          null, new ActivityRegistry("1.0", "other-ns", "other-tq", List.of()));
    }
  }

  @BeforeEach
  void seedPlugin() {
    if (!namespaceRepository.existsById(NAMESPACE)) {
      PlatformNamespaceEntity namespace = new PlatformNamespaceEntity();
      namespace.setId(NAMESPACE);
      namespace.setDisplayName("Plugin Source NS");
      namespace.setCreatedAt(Instant.now());
      namespace.setUpdatedAt(Instant.now());
      namespaceRepository.save(namespace);
    }
    activityCatalog.publishPluginRegistry(
        "demo-plugin",
        new ActivityRegistry(
            "1.0",
            NAMESPACE,
            "demo-tq",
            List.of(
                new ActivityRegistry.ActivityDefinition(
                    "demo.act", "Demo", null, null, "30s", null, null, List.of(), List.of()))));
    PlatformPluginEntity entity = new PlatformPluginEntity();
    entity.setId("demo-plugin");
    entity.setVersion("1.0.0");
    entity.setDisplayName("Demo");
    entity.setNamespace(NAMESPACE);
    entity.setTaskQueue("demo-tq");
    entity.setTypedWorkflows(false);
    entity.setState(PluginState.RUNNING.value());
    entity.setDesiredState(PluginState.RUNNING.value());
    entity.setReplicas(1);
    entity.setJarPath("/tmp/demo.jar");
    entity.setJarSha256("abc");
    entity.setUploadedBy("test");
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());
    pluginRepository.save(entity);
  }

  @Test
  void mapsActivityToPluginSource() {
    var sources = resolver.forNamespace(NAMESPACE);

    assertThat(sources).containsKey("demo.act");
    assertThat(sources.get("demo.act").pluginId()).isEqualTo("demo-plugin");
    assertThat(sources.get("demo.act").version()).isEqualTo("1.0.0");
    assertThat(sources.get("demo.act").runtimeHealthy()).isTrue();
  }
}
