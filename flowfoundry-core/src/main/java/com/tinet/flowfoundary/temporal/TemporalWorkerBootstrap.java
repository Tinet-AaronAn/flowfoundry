package com.tinet.flowfoundary.temporal;

import com.tinet.flowfoundary.config.TemporalProperties;
import com.tinet.flowfoundary.interpreter.FlowInterpreterWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;
import jakarta.annotation.PreDestroy;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class TemporalWorkerBootstrap {

  private static final Logger log = LoggerFactory.getLogger(TemporalWorkerBootstrap.class);

  private final TemporalProperties properties;
  private final WorkflowClient client;
  private final List<TemporalWorkerExtension> extensions;

  private WorkerFactory factory;

  public TemporalWorkerBootstrap(
      TemporalProperties properties,
      WorkflowClient client,
      List<TemporalWorkerExtension> extensions) {
    this.properties = properties;
    this.client = client;
    this.extensions = extensions;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void startWorker() {
    factory = WorkerFactory.newInstance(client);

    WorkerOptions workerOptions =
        WorkerOptions.newBuilder()
            .setMaxConcurrentActivityExecutionSize(properties.maxConcurrentActivities())
            .setMaxConcurrentWorkflowTaskExecutionSize(properties.maxConcurrentWorkflows())
            .build();

    Worker worker = factory.newWorker(properties.taskQueue(), workerOptions);
    worker.registerWorkflowImplementationTypes(FlowInterpreterWorkflowImpl.class);
    for (TemporalWorkerExtension extension : extensions) {
      extension.register(worker);
    }

    factory.start();
    log.info(
        "Temporal worker started host={} namespace={} taskQueue={} extensions={}",
        properties.host(),
        properties.namespace(),
        properties.taskQueue(),
        extensions.size());
  }

  @PreDestroy
  public void shutdown() {
    if (factory != null) {
      factory.shutdown();
      log.info("Temporal worker shutdown complete");
    }
  }
}
