package com.tinet.flowfoundry.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
    RequestContextHolder.resetRequestAttributes();
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
  void resolvesTenantIdHeader() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            CallerAuthentication.forClient(
                new ApiClientService.AuthenticatedApiClient(
                    "svc-a", java.util.Set.of("tenant-a", "tenant-b"), false)));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(PlatformSecurityHeaders.TENANT_ID, "tenant-a");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    assertThat(namespaceAccess.resolveActiveTenantId()).isEqualTo("tenant-a");
  }

  @Test
  void tenantContextListsAllowedTenants() {
    var context = namespaceAccess.tenantContext();
    assertThat(context.allowedTenantIds()).containsExactlyInAnyOrder("alpha", "beta");
    assertThat(context.tenantHeader()).isEqualTo(PlatformSecurityHeaders.TENANT_ID);
  }

  @Test
  void deniesUnknownNamespace() {
    assertThat(namespaceAccess.canAccess("gamma")).isFalse();
    assertThatThrownBy(() -> namespaceAccess.requireAccess("gamma"))
        .isInstanceOf(NamespaceAccessDeniedException.class);
  }
}
