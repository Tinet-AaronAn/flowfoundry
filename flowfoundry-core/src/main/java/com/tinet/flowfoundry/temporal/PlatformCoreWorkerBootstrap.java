package com.tinet.flowfoundry.temporal;

import com.tinet.flowfoundry.activity.ActivityTypes;
import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import com.tinet.flowfoundry.config.TemporalProperties;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Platform Worker for core activities only ({@link ActivityTypes#PLATFORM_TASK_QUEUE}). Polls
 * every known app namespace so core activities can run inside any workflow execution.
 */
@Component
@ConditionalOnFlowFoundryPlatform
public class PlatformCoreWorkerBootstrap {

  private static final Logger log = LoggerFactory.getLogger(PlatformCoreWorkerBootstrap.class);

  private final TemporalProperties properties;
  private final TemporalClients temporalClients;
  private final DeploymentContractRegistry contractRegistry;
  private final CoreTemporalWorkerExtension coreWorkerExtension;

  private final List<WorkerFactory> factories = new ArrayList<>();

  public PlatformCoreWorkerBootstrap(
      TemporalProperties properties,
      TemporalClients temporalClients,
      DeploymentContractRegistry contractRegistry,
      CoreTemporalWorkerExtension coreWorkerExtension) {
    this.properties = properties;
    this.temporalClients = temporalClients;
    this.contractRegistry = contractRegistry;
    this.coreWorkerExtension = coreWorkerExtension;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void startCoreWorkers() {
    Set<String> namespaces = new LinkedHashSet<>(contractRegistry.knownNamespaces());
    for (String namespace : namespaces) {
      if (namespace == null || namespace.isBlank()) {
        continue;
      }
      startCoreWorker(namespace.trim());
    }
  }

  private void startCoreWorker(String namespace) {
    WorkerFactory factory =
        WorkerFactory.newInstance(temporalClients.workflowClient(namespace));
    WorkerOptions workerOptions =
        WorkerOptions.newBuilder()
            .setMaxConcurrentActivityExecutionSize(properties.maxConcurrentActivities())
            .build();

    Worker worker = factory.newWorker(ActivityTypes.PLATFORM_TASK_QUEUE, workerOptions);
    coreWorkerExtension.register(worker);
    factory.start();
    factories.add(factory);
    log.info(
        "Platform core-activity worker started host={} namespace={} taskQueue={}",
        properties.host(),
        namespace,
        ActivityTypes.PLATFORM_TASK_QUEUE);
  }

  @PreDestroy
  public void shutdown() {
    for (WorkerFactory factory : factories) {
      factory.shutdown();
    }
    if (!factories.isEmpty()) {
      log.info("Platform core-activity worker shutdown complete");
    }
  }
}
