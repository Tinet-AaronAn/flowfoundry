package com.tinet.flowfoundry.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

  private final PlatformSecurityProperties properties;
  private final ApiClientService apiClientService;
  private final AuditLogService auditLogService;

  public ApiKeyAuthenticationFilter(
      PlatformSecurityProperties properties,
      ApiClientService apiClientService,
      AuditLogService auditLogService) {
    this.properties = properties;
    this.apiClientService = apiClientService;
    this.auditLogService = auditLogService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!properties.enabled()) {
      SecurityContextHolder.getContext()
          .setAuthentication(CallerAuthentication.forDev(properties.devNamespace()));
      filterChain.doFilter(request, response);
      return;
    }

    String apiKey = resolveApiKey(request);
    if (apiKey == null) {
      filterChain.doFilter(request, response);
      return;
    }

    var client = apiClientService.authenticate(apiKey);
    if (client.isEmpty()) {
      auditLogService.record(
          new AuditLogService.AuditLogEntry(
              null,
              null,
              null,
              AuditActions.AUTH_FAILED,
              null,
              null,
              null,
              request.getMethod(),
              request.getRequestURI(),
              401,
              "Invalid API key",
              request.getRemoteAddr()));
      writeUnauthorized(response, "Invalid API key");
      return;
    }

    apiClientService.touchLastUsed(client.get().clientId());
    SecurityContextHolder.getContext()
        .setAuthentication(CallerAuthentication.forClient(client.get()));
    filterChain.doFilter(request, response);
  }

  private static String resolveApiKey(HttpServletRequest request) {
    String header = request.getHeader(PlatformSecurityHeaders.API_KEY);
    if (header != null && !header.isBlank()) {
      return header.trim();
    }
    String authorization = request.getHeader("Authorization");
    if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
      String token = authorization.substring(7).trim();
      return token.isEmpty() ? null : token;
    }
    return null;
  }

  private static void writeUnauthorized(HttpServletResponse response, String message)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write("{\"message\":\"" + escapeJson(message) + "\"}");
  }

  private static String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
