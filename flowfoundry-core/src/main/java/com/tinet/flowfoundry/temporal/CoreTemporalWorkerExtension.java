package com.tinet.flowfoundry.temporal;

import com.tinet.flowfoundry.activity.CompositeDynamicActivityRouter;
import io.temporal.worker.Worker;
import org.springframework.stereotype.Component;

/** Registers core Temporal Activity implementations from flowfoundry-core. */
@Component
public class CoreTemporalWorkerExtension implements TemporalWorkerExtension {

  private final CompositeDynamicActivityRouter activityRouter;

  public CoreTemporalWorkerExtension(CompositeDynamicActivityRouter activityRouter) {
    this.activityRouter = activityRouter;
  }

  @Override
  public void register(Worker worker) {
    worker.registerActivitiesImplementations(activityRouter);
  }
}
