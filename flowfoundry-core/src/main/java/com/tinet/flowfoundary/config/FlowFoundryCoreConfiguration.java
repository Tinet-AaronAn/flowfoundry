package com.tinet.flowfoundary.config;

import com.tinet.flowfoundary.registry.ActivityRegistry;
import com.tinet.flowfoundary.registry.ActivityRegistryLoader;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan("com.tinet.flowfoundary")
@EnableJpaRepositories(basePackages = "com.tinet.flowfoundary")
@EntityScan(basePackages = "com.tinet.flowfoundary")
@EnableConfigurationProperties(ActivityRegistryProperties.class)
public class FlowFoundryCoreConfiguration {

  @Bean
  ActivityRegistry activityRegistry(ActivityRegistryLoader loader) {
    return loader.load();
  }
}
