package com.tinet.flowfoundry.plugin;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import com.tinet.flowfoundry.registry.ActivityCatalogService;
import com.tinet.flowfoundry.registry.ActivityRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnFlowFoundryPlatform
public class PluginActivitySourceResolver {

  private final ActivityCatalogService activityCatalog;
  private final PlatformPluginRepository pluginRepository;

  public PluginActivitySourceResolver(
      ActivityCatalogService activityCatalog, PlatformPluginRepository pluginRepository) {
    this.activityCatalog = activityCatalog;
    this.pluginRepository = pluginRepository;
  }

  public Map<String, PluginActivitySource> forNamespace(String namespace) {
    String normalized = namespace == null ? "" : namespace.trim();
    Map<String, PluginActivitySource> result = new LinkedHashMap<>();
    for (Map.Entry<String, ActivityRegistry> entry :
        activityCatalog.pluginRegistrySnapshot().entrySet()) {
      ActivityRegistry registry = entry.getValue();
      if (!normalized.equals(registry.namespace())) {
        continue;
      }
      String pluginId = entry.getKey();
      PluginActivitySource source = resolveSource(pluginId);
      for (ActivityRegistry.ActivityDefinition definition : registry.activities()) {
        result.put(definition.id(), source);
      }
    }
    return result;
  }

  private PluginActivitySource resolveSource(String pluginId) {
    return pluginRepository.findByIdOrderByCreatedAtDesc(pluginId).stream()
        .findFirst()
        .map(entity -> toSource(pluginId, entity))
        .orElseGet(
            () -> new PluginActivitySource(pluginId, null, PluginState.READY.value(), false));
  }

  private static PluginActivitySource toSource(String pluginId, PlatformPluginEntity entity) {
    boolean healthy =
        PluginState.RUNNING.value().equals(entity.getState())
            && (entity.getErrorDetail() == null || entity.getErrorDetail().isBlank());
    return new PluginActivitySource(
        pluginId, entity.getVersion(), entity.getState(), healthy);
  }
}
