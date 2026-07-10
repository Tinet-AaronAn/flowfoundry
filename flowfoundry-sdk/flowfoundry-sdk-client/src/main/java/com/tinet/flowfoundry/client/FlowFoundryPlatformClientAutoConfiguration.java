package com.tinet.flowfoundry.client;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ComponentScan(
    basePackageClasses = {
      FlowFoundryPlatformBffController.class,
      com.tinet.flowfoundry.run.RemoteFlowRunRecorder.class
    })
@EnableConfigurationProperties({FlowFoundryPlatformProperties.class, FlowFoundryBffProperties.class})
public class FlowFoundryPlatformClientAutoConfiguration {

  @Bean
  FlowFoundryPlatformClient flowFoundryPlatformClient(FlowFoundryPlatformProperties properties) {
    return new DefaultFlowFoundryPlatformClient(
        DefaultFlowFoundryPlatformClient.buildRestClient(properties), properties);
  }
}
