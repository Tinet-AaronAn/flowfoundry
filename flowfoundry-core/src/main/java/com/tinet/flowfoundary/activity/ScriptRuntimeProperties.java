package com.tinet.flowfoundary.activity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "activity.script-runtime")
public record ScriptRuntimeProperties(String serviceUrl) {}
