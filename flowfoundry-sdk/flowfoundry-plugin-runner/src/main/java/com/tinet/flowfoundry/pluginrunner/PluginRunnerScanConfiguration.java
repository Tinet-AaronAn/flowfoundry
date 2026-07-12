package com.tinet.flowfoundry.pluginrunner;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * Registers {@code @Component} beans from plugin-declared base packages (loaded via {@code
 * loader.path}). SDK packages are scanned by {@link
 * com.tinet.flowfoundry.config.FlowFoundryWorkerComponentScan}.
 */
@Configuration
@EnableConfigurationProperties(PluginRunnerProperties.class)
public class PluginRunnerScanConfiguration {

  @Bean
  static PluginPackageScanner pluginPackageScanner() {
    return new PluginPackageScanner();
  }

  static final class PluginPackageScanner
      implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

    private final AnnotationBeanNameGenerator beanNameGenerator = new AnnotationBeanNameGenerator();
    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
      this.environment = environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
      String raw = environment.getProperty("flowfoundry.plugin.scan-packages");
      Set<String> packages = resolvePackages(raw);
      if (packages.isEmpty()) {
        throw new IllegalStateException(
            "flowfoundry.plugin.scan-packages is required for plugin runner");
      }
      ClassPathScanningCandidateComponentProvider scanner =
          new ClassPathScanningCandidateComponentProvider(false);
      scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
      scanner.addIncludeFilter(new AnnotationTypeFilter(Service.class));
      for (String basePackage : packages) {
        for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
          String beanName = beanNameGenerator.generateBeanName(candidate, registry);
          if (registry.containsBeanDefinition(beanName)) {
            continue;
          }
          registry.registerBeanDefinition(beanName, candidate);
        }
      }
    }

    private static Set<String> resolvePackages(String raw) {
      if (raw == null || raw.isBlank()) {
        return Set.of();
      }
      return Arrays.stream(raw.split(","))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .collect(Collectors.toSet());
    }
  }
}
