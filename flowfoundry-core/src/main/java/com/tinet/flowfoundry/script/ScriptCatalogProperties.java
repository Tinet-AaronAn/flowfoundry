package com.tinet.flowfoundry.script;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "activity.script-runtime.catalog")
public record ScriptCatalogProperties(
    String baseUrl,
    String scriptsPath,
    String versionsPath,
    String httpMethod) {

  public String resolvedScriptsPath() {
    return blank(scriptsPath) ? "/ivr/jsCode/list" : scriptsPath.trim();
  }

  public String resolvedVersionsPath() {
    return blank(versionsPath) ? "/ivr/jsCode/version/list" : versionsPath.trim();
  }

  public String resolvedHttpMethod() {
    return blank(httpMethod) ? "GET" : httpMethod.trim().toUpperCase();
  }

  public boolean enabled() {
    return !blank(baseUrl);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
