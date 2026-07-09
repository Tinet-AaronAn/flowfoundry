package com.tinet.flowfoundry.config;

import com.tinet.flowfoundry.temporal.TemporalClients;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties({TemporalProperties.class})
public class FlowFoundryPlatformConfiguration {

  @Bean
  WorkflowServiceStubs workflowServiceStubs(TemporalProperties properties) {
    return WorkflowServiceStubs.newServiceStubs(
        WorkflowServiceStubsOptions.newBuilder().setTarget(properties.host()).build());
  }

  @Bean
  TemporalClients temporalClients(WorkflowServiceStubs service) {
    return new TemporalClients(service);
  }

  @Bean
  StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
    return new StringRedisTemplate(factory);
  }
}
