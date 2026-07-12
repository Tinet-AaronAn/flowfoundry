package com.tinet.flowfoundry.pluginrunner;

import com.tinet.flowfoundry.sdk.EnableFlowFoundryWorker;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Generic plugin host. Business code ships in a separate plugin jar referenced by {@code
 * -Dloader.path=/plugin/plugin.jar}. Scan packages are supplied via {@code
 * flowfoundry.plugin.scan-packages}.
 */
@SpringBootApplication(
    exclude = {
      SecurityAutoConfiguration.class,
      UserDetailsServiceAutoConfiguration.class
    })
@EnableFlowFoundryWorker
@Import(PluginRunnerScanConfiguration.class)
public class PluginRunnerApplication {

  public static void main(String[] args) {
    SpringApplication.run(PluginRunnerApplication.class, args);
  }
}
