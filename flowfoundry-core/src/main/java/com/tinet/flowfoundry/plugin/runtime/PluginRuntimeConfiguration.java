package com.tinet.flowfoundry.plugin.runtime;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@ConditionalOnFlowFoundryPlatform
@EnableScheduling
public class PluginRuntimeConfiguration {

  @Bean
  @ConditionalOnMissingBean(PluginRuntimeManager.class)
  PluginRuntimeManager noopPluginRuntimeManager(PluginRuntimeProperties properties) {
    return new NoopPluginRuntimeManager(properties);
  }

  static final class NoopPluginRuntimeManager implements PluginRuntimeManager {

    private final PluginRuntimeProperties properties;

    NoopPluginRuntimeManager(PluginRuntimeProperties properties) {
      this.properties = properties;
    }

    @Override
    public void apply(PluginDeployment deployment) {}

    @Override
    public void delete(PluginDeployment deployment) {}

    @Override
    public RuntimeStatus probe(PluginDeployment deployment) {
      return new RuntimeStatus(false, 0, 0, false, "runtime disabled");
    }

    @Override
    public String fetchLogs(PluginDeployment deployment, int tailLines) {
      return properties.isRuntimeEnabled() ? "" : "Plugin runtime is disabled";
    }
  }
}
