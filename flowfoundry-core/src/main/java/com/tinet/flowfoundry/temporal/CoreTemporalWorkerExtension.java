package com.tinet.flowfoundry.temporal;

import com.tinet.flowfoundry.activity.CompositeDynamicActivityRouter;
import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import com.tinet.flowfoundry.run.FlowRunRecorderImpl;
import io.temporal.worker.Worker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Registers core Temporal Activity implementations from flowfoundry-core. */
@Component
@ConditionalOnFlowFoundryPlatform
public class CoreTemporalWorkerExtension implements TemporalWorkerExtension {

  private final CompositeDynamicActivityRouter activityRouter;
  private final ObjectProvider<FlowRunRecorderImpl> flowRunRecorder;

  public CoreTemporalWorkerExtension(
      CompositeDynamicActivityRouter activityRouter,
      ObjectProvider<FlowRunRecorderImpl> flowRunRecorder) {
    this.activityRouter = activityRouter;
    this.flowRunRecorder = flowRunRecorder;
  }

  @Override
  public void register(Worker worker) {
    FlowRunRecorderImpl recorder = flowRunRecorder.getIfAvailable();
    if (recorder != null) {
      worker.registerActivitiesImplementations(activityRouter, recorder);
    } else {
      worker.registerActivitiesImplementations(activityRouter);
    }
  }
}
