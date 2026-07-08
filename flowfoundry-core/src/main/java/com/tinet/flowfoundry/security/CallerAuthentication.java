package com.tinet.flowfoundry.security;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class CallerAuthentication extends AbstractAuthenticationToken {

  private final String apiKeyId;
  private final Set<String> namespaces;
  private final boolean admin;

  private CallerAuthentication(
      String apiKeyId, Set<String> namespaces, boolean admin, Collection<GrantedAuthority> authorities) {
    super(authorities);
    this.apiKeyId = apiKeyId;
    this.namespaces = Set.copyOf(namespaces);
    this.admin = admin;
    setAuthenticated(true);
  }

  public static CallerAuthentication forApiKey(ApiKeyService.AuthenticatedApiKey apiKey) {
    return new CallerAuthentication(
        apiKey.apiKeyId(),
        apiKey.namespaces(),
        apiKey.admin(),
        authorities(apiKey.admin()));
  }

  public static CallerAuthentication forDev(String devNamespace) {
    return new CallerAuthentication(
        "dev", Set.of(devNamespace), true, List.of(role("ROLE_API_KEY"), role("ROLE_PLATFORM_ADMIN")));
  }

  public String apiKeyId() {
    return apiKeyId;
  }

  public Set<String> namespaces() {
    return namespaces;
  }

  public boolean admin() {
    return admin;
  }

  @Override
  public Object getCredentials() {
    return "";
  }

  @Override
  public Object getPrincipal() {
    return apiKeyId;
  }

  private static List<GrantedAuthority> authorities(boolean admin) {
    if (admin) {
      return List.of(role("ROLE_API_KEY"), role("ROLE_PLATFORM_ADMIN"));
    }
    return List.of(role("ROLE_API_KEY"));
  }

  private static SimpleGrantedAuthority role(String role) {
    return new SimpleGrantedAuthority(role);
  }
}
