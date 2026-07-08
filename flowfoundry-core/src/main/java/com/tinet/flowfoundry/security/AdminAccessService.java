package com.tinet.flowfoundry.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AdminAccessService {

  private final PlatformSecurityProperties properties;

  public AdminAccessService(PlatformSecurityProperties properties) {
    this.properties = properties;
  }

  public void requireAdmin() {
    requireLocalhost();
  }

  public void requireLocalhost() {
    if (!properties.adminLocalhostOnly()) {
      return;
    }
    HttpServletRequest request = currentRequest();
    if (request == null) {
      return;
    }
    if (!LocalhostAddresses.isLocalhost(request.getRemoteAddr())) {
      throw new AdminAccessDeniedException();
    }
  }

  public String actorApiKeyId() {
    HttpServletRequest request = currentRequest();
    if (request != null && LocalhostAddresses.isLocalhost(request.getRemoteAddr())) {
      return "localhost-admin";
    }
    return "admin";
  }

  public boolean isLocalAdminRequest() {
    if (!properties.adminLocalhostOnly()) {
      return true;
    }
    HttpServletRequest request = currentRequest();
    return request != null && LocalhostAddresses.isLocalhost(request.getRemoteAddr());
  }

  private static HttpServletRequest currentRequest() {
    if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
      return null;
    }
    return attributes.getRequest();
  }
}
