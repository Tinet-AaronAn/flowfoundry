package com.tinet.flowfoundry.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** 允许业务 iframe 壳（如 :8082）跨域拉取平台 public-config / API。 */
@Configuration
@ConditionalOnFlowFoundryPlatform
public class PlatformCorsConfiguration {

  private static final List<String> DEFAULT_EMBED_ORIGINS =
      List.of(
          "http://127.0.0.1:8082",
          "http://localhost:8082",
          "http://127.0.0.1:8081",
          "http://localhost:8081");

  @Bean
  CorsConfigurationSource corsConfigurationSource(FlowFoundryProperties properties) {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    if (!properties.getModeler().isAllowFrameEmbedding()) {
      return source;
    }

    CorsConfiguration config = new CorsConfiguration();
    List<String> origins = new ArrayList<>(properties.getModeler().getAllowedEmbedOrigins());
    if (origins.isEmpty()) {
      origins.addAll(DEFAULT_EMBED_ORIGINS);
    }
    config.setAllowedOriginPatterns(origins);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setExposedHeaders(List.of("*"));
    config.setAllowCredentials(false);
    config.setMaxAge(3600L);

    source.registerCorsConfiguration("/api/**", config);
    source.registerCorsConfiguration("/assets/**", config);
    return source;
  }
}
