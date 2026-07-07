package com.tinet.flowfoundry.boot;

import com.tinet.flowfoundry.sdk.EnableFlowFoundry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.tinet.flowfoundry")
@EnableFlowFoundry
public class FlowFoundryCoreApplication {

  public static void main(String[] args) {
    SpringApplication.run(FlowFoundryCoreApplication.class, args);
  }
}
