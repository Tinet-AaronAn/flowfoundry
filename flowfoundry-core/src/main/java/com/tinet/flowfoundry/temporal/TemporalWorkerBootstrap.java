package com.tinet.flowfoundry.temporal;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryWorker;
import com.tinet.flowfoundry.config.TemporalProperties;
import com.tinet.flowfoundry.interpreter.FlowInterpreterWorkflowImpl;
import com.tinet.flowfoundry.registry.ActivityRegistry;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Business Worker ({@code run-mode=worker}): polls a single Temporal namespace and task queue
 * derived from the Activity Registry ({@code namespace} + {@code defaultTaskQueue}).
 *
 * <p>Platform core activities ({@code script-runtime}, {@code human-task}) use {@code
 * flowfoundry-platform} and are handled by {@link PlatformCoreWorkerBootstrap} on the platform
 * process.
 */
@Component
@ConditionalOnFlowFoundryWorker
public class TemporalWorkerBootstrap {

  private static final Logger log = LoggerFactory.getLogger(TemporalWorkerBootstrap.class);

  private final TemporalProperties properties;
  private final TemporalClients temporalClients;
  private final ActivityRegistry activityRegistry;
  private final List<TemporalWorkerExtension> extensions;

  private final List<WorkerFactory> factories = new ArrayList<>();

  public TemporalWorkerBootstrap(
      TemporalProperties properties,
      TemporalClients temporalClients,
      ActivityRegistry activityRegistry,
      List<TemporalWorkerExtension> extensions) {
    this.properties = properties;
    this.temporalClients = temporalClients;
    this.activityRegistry = activityRegistry;
    this.extensions = extensions;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void startWorker() {
    startWorkerFactory(activityRegistry.namespace(), activityRegistry.defaultTaskQueue());
  }

  private void startWorkerFactory(String namespace, String taskQueue) {
    WorkerFactory factory =
        WorkerFactory.newInstance(temporalClients.workflowClient(namespace));
    WorkerOptions workerOptions =
        WorkerOptions.newBuilder()
            .setMaxConcurrentActivityExecutionSize(properties.maxConcurrentActivities())
            .setMaxConcurrentWorkflowTaskExecutionSize(properties.maxConcurrentWorkflows())
            .build();

    Worker worker = factory.newWorker(taskQueue, workerOptions);
    worker.registerWorkflowImplementationTypes(FlowInterpreterWorkflowImpl.class);
    for (TemporalWorkerExtension extension : extensions) {
      extension.register(worker);
    }

    factory.start();
    factories.add(factory);
    log.info(
        "Temporal worker started host={} namespace={} taskQueue={} extensions={}",
        properties.host(),
        namespace,
        taskQueue,
        extensions.size());
  }

  @PreDestroy
  public void shutdown() {
    for (WorkerFactory factory : factories) {
      factory.shutdown();
    }
    if (!factories.isEmpty()) {
      log.info("Temporal worker shutdown complete");
    }
  }
}
