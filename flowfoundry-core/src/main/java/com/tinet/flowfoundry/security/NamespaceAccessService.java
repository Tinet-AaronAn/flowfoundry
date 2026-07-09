package com.tinet.flowfoundry.security;

import com.tinet.flowfoundry.registry.ActivityCatalogService;
import com.tinet.flowfoundry.workflow.NamespaceContextDto;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves the namespace-scoped access for the current request. Namespace is the single
 * user-facing isolation unit: workflows, run logs and api keys are all scoped by it.
 */
@Service
public class NamespaceAccessService {

  private final ActivityCatalogService activityCatalog;

  public NamespaceAccessService(ActivityCatalogService activityCatalog) {
    this.activityCatalog = activityCatalog;
  }

  public Set<String> allowedNamespaces() {
    return currentCaller()
        .map(CallerAuthentication::namespaces)
        .orElse(Set.of(defaultNamespace()));
  }

  public boolean canAccess(String namespace) {
    if (namespace == null || namespace.isBlank()) {
      return false;
    }
    return currentCaller()
        .map(caller -> caller.admin() || caller.namespaces().contains(namespace))
        .orElse(true);
  }

  public void requireAccess(String namespace) {
    if (!canAccess(namespace)) {
      throw new NamespaceAccessDeniedException(namespace);
    }
  }

  public String resolveActiveNamespace() {
    CallerAuthentication caller = currentCaller().orElse(null);
    if (caller != null && caller.admin() && caller.namespaces().isEmpty()) {
      return resolveNamespaceHeader().orElse(defaultNamespace());
    }

    Set<String> allowed = allowedNamespaces();
    Optional<String> headerNamespace = resolveNamespaceHeader();
    if (headerNamespace.isPresent()) {
      String namespace = NamespaceIds.normalize(headerNamespace.get());
      requireAccess(namespace);
      return namespace;
    }
    if (allowed.size() == 1) {
      return allowed.iterator().next();
    }
    throw new IllegalArgumentException(
        "Header " + PlatformSecurityHeaders.PLATFORM_NAMESPACE + " is required");
  }

  public void requireAuthenticatedNamespace() {
    resolveActiveNamespace();
  }

  public NamespaceContextDto namespaceContext() {
    Set<String> allowed = allowedNamespaces();
    Optional<String> headerNamespace = resolveNamespaceHeader();
    String active =
        headerNamespace
            .filter(ns -> canAccess(NamespaceIds.normalize(ns)))
            .map(NamespaceIds::normalize)
            .orElseGet(
                () -> {
                  if (allowed.size() == 1) {
                    return allowed.iterator().next();
                  }
                  return null;
                });
    return new NamespaceContextDto(active, allowed, PlatformSecurityHeaders.PLATFORM_NAMESPACE);
  }

  public boolean isAdmin() {
    return currentCaller().map(CallerAuthentication::admin).orElse(false);
  }

  /** Default namespace when the caller has not selected one (admin with access to all). */
  private String defaultNamespace() {
    return activityCatalog.localBusinessNamespace();
  }

  private Optional<String> resolveNamespaceHeader() {
    HttpServletRequest request = currentRequest();
    if (request == null) {
      return Optional.empty();
    }
    // 规范请求头：X-Platform-Namespace（平台唯一的隔离单位）。
    String namespaceHeader = request.getHeader(PlatformSecurityHeaders.PLATFORM_NAMESPACE);
    if (namespaceHeader == null || namespaceHeader.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(namespaceHeader.trim());
  }

  private static Optional<CallerAuthentication> currentCaller() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof CallerAuthentication caller) {
      return Optional.of(caller);
    }
    return Optional.empty();
  }

  private static HttpServletRequest currentRequest() {
    if (!(RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes)) {
      return null;
    }
    return attributes.getRequest();
  }
}
