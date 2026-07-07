package com.tinet.flowfoundry.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class NamespaceAccessService {

  private final PlatformSecurityProperties properties;

  public NamespaceAccessService(PlatformSecurityProperties properties) {
    this.properties = properties;
  }

  public Set<String> allowedNamespaces() {
    return currentCaller().map(CallerAuthentication::namespaces).orElse(Set.of(properties.devNamespace()));
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
      return resolveNamespaceHeader().orElse(properties.devNamespace());
    }

    Set<String> allowed = allowedNamespaces();
    HttpServletRequest request = currentRequest();
    if (request != null) {
      String header = request.getHeader(PlatformSecurityHeaders.PLATFORM_NAMESPACE);
      if (header != null && !header.isBlank()) {
        String namespace = header.trim();
        requireAccess(namespace);
        return namespace;
      }
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

  public boolean isAdmin() {
    return currentCaller().map(CallerAuthentication::admin).orElse(!properties.enabled());
  }

  private java.util.Optional<String> resolveNamespaceHeader() {
    HttpServletRequest request = currentRequest();
    if (request == null) {
      return java.util.Optional.empty();
    }
    String header = request.getHeader(PlatformSecurityHeaders.PLATFORM_NAMESPACE);
    if (header == null || header.isBlank()) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(header.trim());
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
