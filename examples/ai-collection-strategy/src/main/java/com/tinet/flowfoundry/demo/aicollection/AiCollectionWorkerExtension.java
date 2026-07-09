package com.tinet.flowfoundry.demo.aicollection;

import com.tinet.flowfoundry.temporal.TemporalWorkerExtension;
import io.temporal.worker.Worker;
import org.springframework.stereotype.Component;

@Component
public class AiCollectionWorkerExtension implements TemporalWorkerExtension {

  private final CallCampaignActivitiesImpl activities;

  public AiCollectionWorkerExtension(CallCampaignActivitiesImpl activities) {
    this.activities = activities;
  }

  @Override
  public void register(Worker worker) {
    worker.registerWorkflowImplementationTypes(CallCampaignWorkflowImpl.class);
    worker.registerActivitiesImplementations(activities);
  }
}
