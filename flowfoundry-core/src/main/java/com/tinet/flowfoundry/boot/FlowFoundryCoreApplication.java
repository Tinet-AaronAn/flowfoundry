package com.tinet.flowfoundry.boot;

import com.tinet.flowfoundry.config.FlowFoundryCoreConfiguration;
import com.tinet.flowfoundry.config.FlowFoundryPlatformConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = "com.tinet.flowfoundry")
@Import({FlowFoundryCoreConfiguration.class, FlowFoundryPlatformConfiguration.class})
public class FlowFoundryCoreApplication {

  public static void main(String[] args) {
    SpringApplication.run(FlowFoundryCoreApplication.class, args);
  }
}
