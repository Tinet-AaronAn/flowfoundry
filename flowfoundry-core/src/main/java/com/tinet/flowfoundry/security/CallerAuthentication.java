package com.tinet.flowfoundry.security;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class CallerAuthentication extends AbstractAuthenticationToken {

  private final String clientId;
  private final Set<String> namespaces;
  private final boolean admin;

  private CallerAuthentication(
      String clientId, Set<String> namespaces, boolean admin, Collection<GrantedAuthority> authorities) {
    super(authorities);
    this.clientId = clientId;
    this.namespaces = Set.copyOf(namespaces);
    this.admin = admin;
    setAuthenticated(true);
  }

  public static CallerAuthentication forClient(ApiClientService.AuthenticatedApiClient client) {
    return new CallerAuthentication(
        client.clientId(),
        client.namespaces(),
        client.admin(),
        authorities(client.admin()));
  }

  public static CallerAuthentication forDev(String devNamespace) {
    return new CallerAuthentication(
        "dev", Set.of(devNamespace), true, List.of(role("ROLE_API_CLIENT"), role("ROLE_PLATFORM_ADMIN")));
  }

  public String clientId() {
    return clientId;
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
    return clientId;
  }

  private static List<GrantedAuthority> authorities(boolean admin) {
    if (admin) {
      return List.of(role("ROLE_API_CLIENT"), role("ROLE_PLATFORM_ADMIN"));
    }
    return List.of(role("ROLE_API_CLIENT"));
  }

  private static SimpleGrantedAuthority role(String role) {
    return new SimpleGrantedAuthority(role);
  }
}
