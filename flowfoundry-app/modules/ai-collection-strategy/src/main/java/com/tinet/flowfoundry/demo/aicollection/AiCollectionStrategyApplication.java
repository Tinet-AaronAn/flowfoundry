package com.tinet.flowfoundry.demo.aicollection;

import com.tinet.flowfoundry.sdk.EnableFlowFoundryWorker;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(
    scanBasePackages = "com.tinet.flowfoundry.demo.aicollection",
    exclude = {
      SecurityAutoConfiguration.class,
      ManagementWebSecurityAutoConfiguration.class,
      UserDetailsServiceAutoConfiguration.class
    })
@EnableFlowFoundryWorker
public class AiCollectionStrategyApplication {

  public static void main(String[] args) {
    SpringApplication.run(AiCollectionStrategyApplication.class, args);
  }
}
