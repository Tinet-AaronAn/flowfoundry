package com.tinet.flowfoundry.security;

import com.tinet.flowfoundry.workflow.TenantContextDto;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves tenant-scoped workflow namespaces. In the multi-tenant model, {@code tenantId} and
 * workflow {@code namespace} are the same value.
 */
@Service
public class NamespaceAccessService {

  private final PlatformSecurityProperties properties;

  public NamespaceAccessService(PlatformSecurityProperties properties) {
    this.properties = properties;
  }

  public Set<String> allowedNamespaces() {
    return allowedTenantIds();
  }

  public Set<String> allowedTenantIds() {
    return currentCaller().map(CallerAuthentication::namespaces).orElse(Set.of(properties.devNamespace()));
  }

  public boolean canAccess(String namespace) {
    return canAccessTenant(namespace);
  }

  public boolean canAccessTenant(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      return false;
    }
    return currentCaller()
        .map(caller -> caller.admin() || caller.namespaces().contains(tenantId))
        .orElse(true);
  }

  public void requireAccess(String namespace) {
    requireTenantAccess(namespace);
  }

  public void requireTenantAccess(String tenantId) {
    if (!canAccessTenant(tenantId)) {
      throw new NamespaceAccessDeniedException(tenantId);
    }
  }

  public String resolveActiveNamespace() {
    return resolveActiveTenantId();
  }

  public String resolveActiveTenantId() {
    CallerAuthentication caller = currentCaller().orElse(null);
    if (caller != null && caller.admin() && caller.namespaces().isEmpty()) {
      return resolveTenantHeader().orElse(properties.devNamespace());
    }

    Set<String> allowed = allowedTenantIds();
    java.util.Optional<String> headerTenant = resolveTenantHeader();
    if (headerTenant.isPresent()) {
      String tenantId = TenantIds.normalize(headerTenant.get());
      requireTenantAccess(tenantId);
      return tenantId;
    }
    if (allowed.size() == 1) {
      return allowed.iterator().next();
    }
    throw new IllegalArgumentException(
        "Header "
            + PlatformSecurityHeaders.TENANT_ID
            + " (or "
            + PlatformSecurityHeaders.PLATFORM_NAMESPACE
            + ") is required");
  }

  public void requireAuthenticatedNamespace() {
    resolveActiveTenantId();
  }

  public TenantContextDto tenantContext() {
    Set<String> allowed = allowedTenantIds();
    java.util.Optional<String> headerTenant = resolveTenantHeader();
    String active =
        headerTenant
            .filter(tenant -> canAccessTenant(TenantIds.normalize(tenant)))
            .map(TenantIds::normalize)
            .orElseGet(
                () -> {
                  if (allowed.size() == 1) {
                    return allowed.iterator().next();
                  }
                  return null;
                });
    return new TenantContextDto(active, allowed, PlatformSecurityHeaders.TENANT_ID);
  }

  public boolean isAdmin() {
    return currentCaller().map(CallerAuthentication::admin).orElse(!properties.enabled());
  }

  private java.util.Optional<String> resolveTenantHeader() {
    HttpServletRequest request = currentRequest();
    if (request == null) {
      return java.util.Optional.empty();
    }
    String tenantHeader = request.getHeader(PlatformSecurityHeaders.TENANT_ID);
    if (tenantHeader != null && !tenantHeader.isBlank()) {
      return java.util.Optional.of(tenantHeader.trim());
    }
    String namespaceHeader = request.getHeader(PlatformSecurityHeaders.PLATFORM_NAMESPACE);
    if (namespaceHeader == null || namespaceHeader.isBlank()) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(namespaceHeader.trim());
  }

  private static java.util.Optional<CallerAuthentication> currentCaller() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof CallerAuthentication caller) {
      return java.util.Optional.of(caller);
    }
    return java.util.Optional.empty();
  }

  private static HttpServletRequest currentRequest() {
    if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
      return null;
    }
    return attributes.getRequest();
  }
}
