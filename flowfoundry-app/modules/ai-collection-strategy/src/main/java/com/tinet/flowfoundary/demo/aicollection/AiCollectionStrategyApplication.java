package com.tinet.flowfoundary.demo.aicollection;

import com.tinet.flowfoundary.config.FlowFoundryCoreConfiguration;
import com.tinet.flowfoundary.config.FlowFoundryPlatformConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = "com.tinet.flowfoundary")
@Import({FlowFoundryCoreConfiguration.class, FlowFoundryPlatformConfiguration.class})
public class AiCollectionStrategyApplication {

  public static void main(String[] args) {
    SpringApplication.run(AiCollectionStrategyApplication.class, args);
  }
}
