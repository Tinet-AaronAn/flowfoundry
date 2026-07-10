package com.tinet.flowfoundry.temporal;

import com.tinet.flowfoundry.activity.CompositeDynamicActivityRouter;
import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryWorker;
import com.tinet.flowfoundry.run.FlowRunRecorder;
import io.temporal.worker.Worker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Registers the dynamic Activity router on the business Worker task queue. */
@Component
@ConditionalOnFlowFoundryWorker
public class BusinessTemporalWorkerExtension implements TemporalWorkerExtension {

  private final CompositeDynamicActivityRouter activityRouter;
  private final ObjectProvider<FlowRunRecorder> flowRunRecorder;

  public BusinessTemporalWorkerExtension(
      CompositeDynamicActivityRouter activityRouter,
      ObjectProvider<FlowRunRecorder> flowRunRecorder) {
    this.activityRouter = activityRouter;
    this.flowRunRecorder = flowRunRecorder;
  }

  @Override
  public void register(Worker worker) {
    FlowRunRecorder recorder = flowRunRecorder.getIfAvailable();
    if (recorder != null) {
      worker.registerActivitiesImplementations(activityRouter, recorder);
    } else {
      worker.registerActivitiesImplementations(activityRouter);
    }
  }
}
