package com.tinet.flowfoundry.plugin;

/** Activity catalog hint: which plugin provides a business activity and its runtime health. */
public record PluginActivitySource(
    String pluginId, String version, String state, boolean runtimeHealthy) {}
