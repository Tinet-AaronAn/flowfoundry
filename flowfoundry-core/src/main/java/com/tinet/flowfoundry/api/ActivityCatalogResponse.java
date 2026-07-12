package com.tinet.flowfoundry.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.tinet.flowfoundry.plugin.PluginActivitySource;
import com.tinet.flowfoundry.registry.ActivityRegistry;
import java.util.Map;

/** Activity registry for the active namespace plus plugin source hints for the modeler. */
public record ActivityCatalogResponse(
    @JsonUnwrapped ActivityRegistry registry,
    Map<String, PluginActivitySource> pluginSources) {}
