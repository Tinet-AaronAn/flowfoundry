package com.tinet.flowfoundary.boot;

import com.tinet.flowfoundary.config.FlowFoundryCoreConfiguration;
import com.tinet.flowfoundary.config.FlowFoundryPlatformConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = "com.tinet.flowfoundary")
@Import({FlowFoundryCoreConfiguration.class, FlowFoundryPlatformConfiguration.class})
public class FlowFoundryCoreApplication {

  public static void main(String[] args) {
    SpringApplication.run(FlowFoundryCoreApplication.class, args);
  }
}
