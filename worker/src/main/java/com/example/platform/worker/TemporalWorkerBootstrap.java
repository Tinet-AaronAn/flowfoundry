package com.example.platform.worker;

import com.example.platform.callcampaign.CallCampaignActivitiesImpl;
import com.example.platform.callcampaign.CallCampaignWorkflowImpl;
import com.example.platform.config.TemporalProperties;
import com.example.platform.interpreter.DynamicActivityRouterImpl;
import com.example.platform.interpreter.FlowInterpreterWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;
import jakarta.annotation.PreDestroy;
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
  private final CallCampaignActivitiesImpl activities;
  private final DynamicActivityRouterImpl dynamicActivityRouter;

  private WorkerFactory factory;

  public TemporalWorkerBootstrap(
      TemporalProperties properties,
      WorkflowClient client,
      CallCampaignActivitiesImpl activities,
      DynamicActivityRouterImpl dynamicActivityRouter) {
    this.properties = properties;
    this.client = client;
    this.activities = activities;
    this.dynamicActivityRouter = dynamicActivityRouter;
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
    worker.registerWorkflowImplementationTypes(
        CallCampaignWorkflowImpl.class, FlowInterpreterWorkflowImpl.class);
    worker.registerActivitiesImplementations(activities, dynamicActivityRouter);

    factory.start();
    log.info(
        "Temporal worker started host={} namespace={} taskQueue={}",
        properties.host(),
        properties.namespace(),
        properties.taskQueue());
  }

  @PreDestroy
  public void shutdown() {
    if (factory != null) {
      factory.shutdown();
      log.info("Temporal worker shutdown complete");
    }
  }
}
