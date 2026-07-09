package com.tinet.flowfoundry.security;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import com.tinet.flowfoundry.config.NamespaceRoutingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnFlowFoundryPlatform
public class NamespaceBootstrapRunner {

  private static final Logger log = LoggerFactory.getLogger(NamespaceBootstrapRunner.class);

  private final NamespaceAdminService namespaceAdminService;
  private final NamespaceRoutingProperties namespaceRouting;

  public NamespaceBootstrapRunner(
      NamespaceAdminService namespaceAdminService, NamespaceRoutingProperties namespaceRouting) {
    this.namespaceAdminService = namespaceAdminService;
    this.namespaceRouting = namespaceRouting;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void bootstrapNamespaces() {
    String systemNamespace = namespaceRouting.system();
    namespaceAdminService.ensureRegistered(
        systemNamespace, systemNamespace, "Platform management and modeler debug runs");
    log.info("Ensured system namespace is registered: {}", systemNamespace);
  }
}
