package com.tinet.flowfoundry.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Worker 进程仅暴露健康检查与业务静态壳页面，不提供平台 API。 */
@Configuration
@EnableWebSecurity
@ConditionalOnFlowFoundryWorker
public class FlowFoundryWorkerSecurityConfiguration {

  @Bean
  SecurityFilterChain workerSecurityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }
}
