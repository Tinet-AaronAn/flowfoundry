package com.example.platform.config;

import com.example.platform.registry.ActivityRegistry;
import com.example.platform.registry.ActivityRegistryLoader;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties({TemporalProperties.class, ActivityRegistryProperties.class})
public class PlatformConfiguration {

  @Bean
  ActivityRegistry activityRegistry(ActivityRegistryLoader loader) {
    return loader.load();
  }

  @Bean
  WorkflowServiceStubs workflowServiceStubs(TemporalProperties properties) {
    return WorkflowServiceStubs.newServiceStubs(
        WorkflowServiceStubsOptions.newBuilder().setTarget(properties.host()).build());
  }

  @Bean
  WorkflowClient workflowClient(WorkflowServiceStubs service, TemporalProperties properties) {
    return WorkflowClient.newInstance(
        service, WorkflowClientOptions.newBuilder().setNamespace(properties.namespace()).build());
  }

  @Bean
  StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
    return new StringRedisTemplate(factory);
  }
}
