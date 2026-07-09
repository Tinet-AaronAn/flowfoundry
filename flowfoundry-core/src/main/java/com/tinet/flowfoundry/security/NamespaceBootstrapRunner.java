package com.tinet.flowfoundry.security;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import com.tinet.flowfoundry.registry.ActivityCatalogService;
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
  private final ActivityCatalogService activityCatalog;

  public NamespaceBootstrapRunner(
      NamespaceAdminService namespaceAdminService, ActivityCatalogService activityCatalog) {
    this.namespaceAdminService = namespaceAdminService;
    this.activityCatalog = activityCatalog;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void bootstrapNamespaces() {
    String namespace = activityCatalog.localBusinessNamespace();
    namespaceAdminService.ensureRegistered(
        namespace, namespace, "App namespace (workflows, Temporal, Activity Registry)");
    log.info("Ensured app namespace is registered: {}", namespace);
  }
}
