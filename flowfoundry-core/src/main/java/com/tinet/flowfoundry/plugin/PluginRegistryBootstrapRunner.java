package com.tinet.flowfoundry.plugin;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import com.tinet.flowfoundry.registry.ActivityCatalogService;
import com.tinet.flowfoundry.registry.ActivityRegistry;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Republishes stored plugin registries into the modeler activity catalog after a platform restart. */
@Component
@ConditionalOnFlowFoundryPlatform
public class PluginRegistryBootstrapRunner {

  private static final Logger log = LoggerFactory.getLogger(PluginRegistryBootstrapRunner.class);

  private final PlatformPluginRepository pluginRepository;
  private final PluginStorageService storageService;
  private final PluginPackageInspector packageInspector;
  private final ActivityCatalogService activityCatalog;

  public PluginRegistryBootstrapRunner(
      PlatformPluginRepository pluginRepository,
      PluginStorageService storageService,
      PluginPackageInspector packageInspector,
      ActivityCatalogService activityCatalog) {
    this.pluginRepository = pluginRepository;
    this.storageService = storageService;
    this.packageInspector = packageInspector;
    this.activityCatalog = activityCatalog;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void publishStoredPluginRegistries() {
    Map<String, PlatformPluginEntity> currentByPlugin = new LinkedHashMap<>();
    pluginRepository.findAll().stream()
        .filter(entity -> !PluginState.FAILED.value().equals(entity.getState()))
        .sorted(Comparator.comparing(PlatformPluginEntity::getCreatedAt).reversed())
        .forEach(entity -> currentByPlugin.putIfAbsent(entity.getId(), entity));
    int published = 0;
    for (PlatformPluginEntity entity : currentByPlugin.values()) {
      try {
        byte[] content = storageService.read(entity.getId(), entity.getVersion());
        ActivityRegistry registry = packageInspector.inspect(content).registry();
        activityCatalog.publishPluginRegistry(entity.getId(), registry);
        published++;
      } catch (RuntimeException e) {
        log.warn(
            "Failed to publish plugin registry at startup id={} version={}: {}",
            entity.getId(),
            entity.getVersion(),
            e.getMessage());
      }
    }
    if (!currentByPlugin.isEmpty()) {
      log.info("Published {}/{} plugin registries at startup", published, currentByPlugin.size());
    }
  }
}
