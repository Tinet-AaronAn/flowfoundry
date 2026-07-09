package com.tinet.flowfoundry.temporal;

import com.tinet.flowfoundry.activity.CompositeDynamicActivityRouter;
import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryWorker;
import io.temporal.worker.Worker;
import org.springframework.stereotype.Component;

/** Registers the dynamic Activity router on the business Worker task queue. */
@Component
@ConditionalOnFlowFoundryWorker
public class BusinessTemporalWorkerExtension implements TemporalWorkerExtension {

  private final CompositeDynamicActivityRouter activityRouter;

  public BusinessTemporalWorkerExtension(CompositeDynamicActivityRouter activityRouter) {
    this.activityRouter = activityRouter;
  }

  @Override
  public void register(Worker worker) {
    worker.registerActivitiesImplementations(activityRouter);
  }
}
