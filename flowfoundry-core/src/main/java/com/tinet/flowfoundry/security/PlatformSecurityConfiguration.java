package com.tinet.flowfoundry.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import com.tinet.flowfoundry.config.FlowFoundryProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@ConditionalOnFlowFoundryPlatform
@EnableWebSecurity
@EnableConfigurationProperties({PlatformSecurityProperties.class, FlowFoundryProperties.class})
public class PlatformSecurityConfiguration {

  @Bean
  SecurityFilterChain platformSecurityFilterChain(
      HttpSecurity http,
      PlatformSecurityProperties properties,
      FlowFoundryProperties flowFoundryProperties,
      ApiKeyService apiKeyService,
      AuditLogService auditLogService,
      ObjectMapper objectMapper)
      throws Exception {
    ApiKeyAuthenticationFilter apiKeyFilter =
        new ApiKeyAuthenticationFilter(apiKeyService, auditLogService);
    AuditLoggingFilter auditFilter = new AuditLoggingFilter(auditLogService);
    LocalhostAdminFilter localhostAdminFilter = new LocalhostAdminFilter(properties);
    http.csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    configureFrameHeaders(http, flowFoundryProperties);

    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/actuator/**",
                        "/error",
                        "/",
                        "/index.html",
                        "/modeler/**",
                        "/app/**",
                        "/assets/**",
                        "/api/platform/public-config",
                        "/api/platform/auth.js",
                        "/api/internal/plugins/**",
                        "/api/admin/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            handler ->
                handler.authenticationEntryPoint(
                    (request, response, ex) ->
                        writeJson(
                            response,
                            objectMapper,
                            HttpServletResponse.SC_UNAUTHORIZED,
                            "API key is required")))
        .addFilterBefore(localhostAdminFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(auditFilter, ApiKeyAuthenticationFilter.class);
    return http.build();
  }

  private static void configureFrameHeaders(
      HttpSecurity http, FlowFoundryProperties flowFoundryProperties) throws Exception {
    if (flowFoundryProperties.getModeler().isAllowFrameEmbedding()) {
      http.headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable));
      return;
    }
    http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
  }

  private static void writeJson(
      HttpServletResponse response, ObjectMapper objectMapper, int status, String message)
      throws java.io.IOException {
    response.setStatus(status);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(), Map.of("message", message));
  }
}
