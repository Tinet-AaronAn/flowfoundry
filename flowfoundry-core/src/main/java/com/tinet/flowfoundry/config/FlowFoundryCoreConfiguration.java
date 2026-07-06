package com.tinet.flowfoundry.config;

import com.tinet.flowfoundry.registry.ActivityRegistry;
import com.tinet.flowfoundry.registry.ActivityRegistryLoader;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan("com.tinet.flowfoundry")
@EnableJpaRepositories(basePackages = "com.tinet.flowfoundry")
@EntityScan(basePackages = "com.tinet.flowfoundry")
@EnableConfigurationProperties(ActivityRegistryProperties.class)
public class FlowFoundryCoreConfiguration {

  @Bean
  ActivityRegistry activityRegistry(ActivityRegistryLoader loader) {
    return loader.load();
  }
}
