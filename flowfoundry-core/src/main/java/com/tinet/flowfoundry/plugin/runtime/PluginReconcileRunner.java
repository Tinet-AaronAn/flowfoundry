package com.tinet.flowfoundry.plugin.runtime;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodic reconciliation between DB desired state and Kubernetes Deployments. */
@Component
@ConditionalOnFlowFoundryPlatform
public class PluginReconcileRunner {

  private static final Logger log = LoggerFactory.getLogger(PluginReconcileRunner.class);

  private final PluginReconcileService reconcileService;
  private final PluginRuntimeProperties properties;

  public PluginReconcileRunner(
      PluginReconcileService reconcileService, PluginRuntimeProperties properties) {
    this.reconcileService = reconcileService;
    this.properties = properties;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void reconcileOnStartup() {
    if (!properties.isRuntimeEnabled()) {
      return;
    }
    try {
      reconcileService.reconcileAll();
    } catch (RuntimeException e) {
      log.warn("Plugin reconcile on startup failed: {}", e.getMessage());
    }
  }

  @Scheduled(fixedDelayString = "${flowfoundry.plugins.runtime.reconcile-interval-ms:15000}")
  public void reconcileScheduled() {
    if (!properties.isRuntimeEnabled()) {
      return;
    }
    try {
      reconcileService.reconcileAll();
    } catch (RuntimeException e) {
      log.warn("Plugin reconcile failed: {}", e.getMessage());
    }
  }
}
