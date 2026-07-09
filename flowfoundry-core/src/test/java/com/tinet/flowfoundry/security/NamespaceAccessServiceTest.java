package com.tinet.flowfoundry.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tinet.flowfoundry.config.NamespaceRoutingProperties;
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
    NamespaceRoutingProperties namespaceRouting = new NamespaceRoutingProperties();
    namespaceRouting.setSystem("flowfoundry-system");
    namespaceAccess = new NamespaceAccessService(namespaceRouting);
    SecurityContextHolder.getContext()
        .setAuthentication(
            CallerAuthentication.forApiKey(
                new ApiKeyService.AuthenticatedApiKey(
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
            CallerAuthentication.forApiKey(
                new ApiKeyService.AuthenticatedApiKey(
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
            CallerAuthentication.forApiKey(
                new ApiKeyService.AuthenticatedApiKey(
                    "admin", java.util.Set.of(), true)));
    assertThat(namespaceAccess.canAccess("gamma")).isTrue();
  }

  @Test
  void resolvesNamespaceHeader() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            CallerAuthentication.forApiKey(
                new ApiKeyService.AuthenticatedApiKey(
                    "svc-a", java.util.Set.of("ns-a", "ns-b"), false)));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(PlatformSecurityHeaders.PLATFORM_NAMESPACE, "ns-a");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    assertThat(namespaceAccess.resolveActiveNamespace()).isEqualTo("ns-a");
  }

  @Test
  void namespaceContextListsAllowedNamespaces() {
    var context = namespaceAccess.namespaceContext();
    assertThat(context.allowedNamespaces()).containsExactlyInAnyOrder("alpha", "beta");
    assertThat(context.namespaceHeader()).isEqualTo(PlatformSecurityHeaders.PLATFORM_NAMESPACE);
  }

  @Test
  void deniesUnknownNamespace() {
    assertThat(namespaceAccess.canAccess("gamma")).isFalse();
    assertThatThrownBy(() -> namespaceAccess.requireAccess("gamma"))
        .isInstanceOf(NamespaceAccessDeniedException.class);
  }
}
