package com.tinet.flowfoundry.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

class NamespaceAccessServiceTest {

  private NamespaceAccessService namespaceAccess;

  @BeforeEach
  void setUp() {
    PlatformSecurityProperties properties = new PlatformSecurityProperties();
    properties.setEnabled(true);
    properties.setDevNamespace("default");
    namespaceAccess = new NamespaceAccessService(properties);
    SecurityContextHolder.getContext()
        .setAuthentication(
            CallerAuthentication.forClient(
                new ApiClientService.AuthenticatedApiClient(
                    "svc-b", java.util.Set.of("alpha", "beta"), false)));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void resolvesSingleNamespaceWithoutHeader() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            CallerAuthentication.forClient(
                new ApiClientService.AuthenticatedApiClient(
                    "svc-a", java.util.Set.of("alpha"), false)));
    assertThat(namespaceAccess.resolveActiveNamespace()).isEqualTo("alpha");
  }

  @Test
  void requiresHeaderWhenMultipleNamespaces() {
    assertThatThrownBy(() -> namespaceAccess.resolveActiveNamespace())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void adminCanAccessAnyNamespace() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            CallerAuthentication.forClient(
                new ApiClientService.AuthenticatedApiClient(
                    "admin", java.util.Set.of(), true)));
    assertThat(namespaceAccess.canAccess("gamma")).isTrue();
  }

  @Test
  void deniesUnknownNamespace() {
    assertThat(namespaceAccess.canAccess("gamma")).isFalse();
    assertThatThrownBy(() -> namespaceAccess.requireAccess("gamma"))
        .isInstanceOf(NamespaceAccessDeniedException.class);
  }
}
