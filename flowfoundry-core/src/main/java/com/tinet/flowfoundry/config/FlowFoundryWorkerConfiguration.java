package com.tinet.flowfoundry.config;

import com.tinet.flowfoundry.registry.ActivityRegistry;
import com.tinet.flowfoundry.registry.ActivityRegistryLoader;
import com.tinet.flowfoundry.security.PlatformSecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 业务 Worker 模式：仅加载 Temporal Worker、Activity 路由与幂等，不启动平台 REST / Workflow 持久化。
 */
@Configuration
@ConditionalOnFlowFoundryWorker
@Import({
  FlowFoundryPlatformConfiguration.class,
  FlowFoundryWorkerComponentScan.class
})
@EnableConfigurationProperties({
  FlowFoundryProperties.class,
  ActivityRegistryProperties.class,
  PlatformSecurityProperties.class,
  TemporalProperties.class
})
public class FlowFoundryWorkerConfiguration {

  @Bean
  ActivityRegistry activityRegistry(ActivityRegistryLoader loader) {
    return loader.load();
  }
}
