package com.tinet.flowfoundry.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class LocalhostAdminFilter extends OncePerRequestFilter {

  private final PlatformSecurityProperties properties;

  public LocalhostAdminFilter(PlatformSecurityProperties properties) {
    this.properties = properties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/admin");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!properties.adminLocalhostOnly()) {
      filterChain.doFilter(request, response);
      return;
    }
    if (!LocalhostAddresses.isLocalhost(request.getRemoteAddr())) {
      writeForbidden(response);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private static void writeForbidden(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response
        .getWriter()
        .write("{\"message\":\"Admin API is only available from 127.0.0.1\"}");
  }
}
