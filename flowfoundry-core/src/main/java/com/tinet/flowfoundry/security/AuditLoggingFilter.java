package com.tinet.flowfoundry.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

public class AuditLoggingFilter extends OncePerRequestFilter {

  private final PlatformSecurityProperties properties;
  private final AuditLogService auditLogService;

  public AuditLoggingFilter(
      PlatformSecurityProperties properties, AuditLogService auditLogService) {
    this.properties = properties;
    this.auditLogService = auditLogService;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/actuator/")
        || path.startsWith("/assets/")
        || path.equals("/")
        || path.equals("/index.html")
        || path.startsWith("/modeler/")
        || path.startsWith("/app/")
        || path.equals("/api/platform/public-config")
        || path.equals("/api/platform/auth.js");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!properties.enabled()) {
      filterChain.doFilter(request, response);
      return;
    }

    ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
    try {
      filterChain.doFilter(request, wrapped);
    } finally {
      recordIfNeeded(request, wrapped);
      wrapped.copyBodyToResponse();
    }
  }

  private void recordIfNeeded(HttpServletRequest request, ContentCachingResponseWrapper response) {
    String path = request.getRequestURI();
    if (!path.startsWith("/api/")) {
      return;
    }
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String clientId =
        authentication instanceof CallerAuthentication caller ? caller.clientId() : null;
    if (clientId == null && response.getStatus() != 401) {
      return;
    }
    String namespace = request.getHeader(PlatformSecurityHeaders.PLATFORM_NAMESPACE);
    auditLogService.record(
        new AuditLogService.AuditLogEntry(
            null,
            clientId,
            clientId,
            AuditActions.API_CALL,
            null,
            null,
            namespace,
            request.getMethod(),
            path,
            response.getStatus(),
            null,
            request.getRemoteAddr()));
  }
}
