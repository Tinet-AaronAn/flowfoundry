package com.tinet.flowfoundry.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flowfoundry.platform")
public class FlowFoundryPlatformProperties {

  private String baseUrl = "http://127.0.0.1:8081";
  private String apiKey = "";
  private String namespace = "";
  private String connectTimeout = "5s";
  private String readTimeout = "30s";

  public String baseUrl() {
    return trimTrailingSlash(baseUrl);
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String apiKey() {
    return apiKey == null ? "" : apiKey.trim();
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String namespace() {
    return namespace == null ? "" : namespace.trim();
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String connectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(String connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public String readTimeout() {
    return readTimeout;
  }

  public void setReadTimeout(String readTimeout) {
    this.readTimeout = readTimeout;
  }

  private static String trimTrailingSlash(String value) {
    if (value == null || value.isBlank()) {
      return "http://127.0.0.1:8081";
    }
    String trimmed = value.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }
}
