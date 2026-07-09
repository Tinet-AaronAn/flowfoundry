package com.tinet.flowfoundry.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flowfoundry.security")
public class PlatformSecurityProperties {

  private boolean adminLocalhostOnly = true;
  private List<ApiKeyProperties> apiKeys = new ArrayList<>();

  public boolean adminLocalhostOnly() {
    return adminLocalhostOnly;
  }

  public void setAdminLocalhostOnly(boolean adminLocalhostOnly) {
    this.adminLocalhostOnly = adminLocalhostOnly;
  }

  public List<ApiKeyProperties> apiKeys() {
    return apiKeys == null ? List.of() : apiKeys;
  }

  public void setApiKeys(List<ApiKeyProperties> apiKeys) {
    this.apiKeys = apiKeys;
  }

  public static class ApiKeyProperties {

    private String id;
    private String key;
    private boolean admin;
    private List<String> namespaces = new ArrayList<>();

    public String id() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
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
