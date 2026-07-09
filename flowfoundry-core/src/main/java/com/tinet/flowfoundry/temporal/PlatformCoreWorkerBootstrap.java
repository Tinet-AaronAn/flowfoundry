package com.tinet.flowfoundry.temporal;

import com.tinet.flowfoundry.activity.ActivityTypes;
import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import com.tinet.flowfoundry.config.NamespaceRoutingProperties;
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
 * 平台（flowfoundry-core {@code run-mode=platform}）专属 Worker：仅轮询 {@link
 * ActivityTypes#PLATFORM_TASK_QUEUE}，执行 {@code script-runtime}、{@code human-task} 等 core
 * Activity。业务 Worker 只轮询各自业务 Task Queue，不负责此队列。
 *
 * <p>生产运行落在业务 Temporal namespace，core Activity 仍投递到 {@code flowfoundry-platform}，因此平台
 * Worker 需在系统 namespace 与各已注册业务 namespace 上均轮询该队列。
 */
@Component
@ConditionalOnFlowFoundryPlatform
public class PlatformCoreWorkerBootstrap {

  private static final Logger log = LoggerFactory.getLogger(PlatformCoreWorkerBootstrap.class);

  private final TemporalProperties properties;
  private final TemporalClients temporalClients;
  private final NamespaceRoutingProperties namespaceRouting;
  private final DeploymentContractRegistry contractRegistry;
  private final CoreTemporalWorkerExtension coreWorkerExtension;

  private final List<WorkerFactory> factories = new ArrayList<>();

  public PlatformCoreWorkerBootstrap(
      TemporalProperties properties,
      TemporalClients temporalClients,
      NamespaceRoutingProperties namespaceRouting,
      DeploymentContractRegistry contractRegistry,
      CoreTemporalWorkerExtension coreWorkerExtension) {
    this.properties = properties;
    this.temporalClients = temporalClients;
    this.namespaceRouting = namespaceRouting;
    this.contractRegistry = contractRegistry;
    this.coreWorkerExtension = coreWorkerExtension;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void startCoreWorkers() {
    Set<String> namespaces = new LinkedHashSet<>();
    namespaces.add(contractRegistry.systemNamespace());
    namespaces.addAll(contractRegistry.businessNamespaces());
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
