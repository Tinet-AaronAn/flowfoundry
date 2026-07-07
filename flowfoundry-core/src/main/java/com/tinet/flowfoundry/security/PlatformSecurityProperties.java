package com.tinet.flowfoundry.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flowfoundry.security")
public class PlatformSecurityProperties {

  private boolean enabled = false;
  private String devNamespace = "default";
  private String bootstrapAdminKey = "";
  private boolean adminLocalhostOnly = true;
  private List<ApiKeyClientProperties> apiKeys = new ArrayList<>();

  public boolean enabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String devNamespace() {
    return devNamespace == null || devNamespace.isBlank() ? "default" : devNamespace.trim();
  }

  public void setDevNamespace(String devNamespace) {
    this.devNamespace = devNamespace;
  }

  public String bootstrapAdminKey() {
    return bootstrapAdminKey == null ? "" : bootstrapAdminKey;
  }

  public void setBootstrapAdminKey(String bootstrapAdminKey) {
    this.bootstrapAdminKey = bootstrapAdminKey;
  }

  public boolean adminLocalhostOnly() {
    return adminLocalhostOnly;
  }

  public void setAdminLocalhostOnly(boolean adminLocalhostOnly) {
    this.adminLocalhostOnly = adminLocalhostOnly;
  }

  public List<ApiKeyClientProperties> apiKeys() {
    return apiKeys == null ? List.of() : apiKeys;
  }

  public void setApiKeys(List<ApiKeyClientProperties> apiKeys) {
    this.apiKeys = apiKeys;
  }

  public static class ApiKeyClientProperties {

    private String clientId;
    private String key;
    private boolean admin;
    private List<String> namespaces = new ArrayList<>();

    public String clientId() {
      return clientId;
    }

    public void setClientId(String clientId) {
      this.clientId = clientId;
    }

    public String key() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }

    public boolean admin() {
      return admin;
    }

    public void setAdmin(boolean admin) {
      this.admin = admin;
    }

    public List<String> namespaces() {
      return namespaces == null ? List.of() : namespaces;
    }

    public void setNamespaces(List<String> namespaces) {
      this.namespaces = namespaces;
    }
  }
}
