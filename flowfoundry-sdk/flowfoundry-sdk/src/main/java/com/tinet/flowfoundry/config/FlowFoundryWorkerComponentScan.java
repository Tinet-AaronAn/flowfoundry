package com.tinet.flowfoundry.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnFlowFoundryWorker
@ComponentScan(
    basePackages = {
      "com.tinet.flowfoundry.activity",
      "com.tinet.flowfoundry.temporal",
      "com.tinet.flowfoundry.idempotency",
      "com.tinet.flowfoundry.registry"
    })
public class FlowFoundryWorkerComponentScan {}
