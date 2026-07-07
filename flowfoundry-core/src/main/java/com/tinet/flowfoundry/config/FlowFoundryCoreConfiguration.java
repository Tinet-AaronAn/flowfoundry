package com.tinet.flowfoundry.config;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import com.tinet.flowfoundry.registry.ActivityRegistry;
import com.tinet.flowfoundry.registry.ActivityRegistryLoader;
import com.tinet.flowfoundry.security.PlatformSecurityProperties;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ConditionalOnFlowFoundryPlatform
@ComponentScan("com.tinet.flowfoundry")
@EnableJpaRepositories(basePackages = "com.tinet.flowfoundry")
@EntityScan(basePackages = "com.tinet.flowfoundry")
@EnableConfigurationProperties({
  FlowFoundryProperties.class,
  ActivityRegistryProperties.class,
  PlatformSecurityProperties.class
})
public class FlowFoundryCoreConfiguration {

  @Bean
  ActivityRegistry activityRegistry(ActivityRegistryLoader loader) {
    return loader.load();
  }
}
