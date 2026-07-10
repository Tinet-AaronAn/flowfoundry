package com.tinet.flowfoundry.run;

import com.tinet.flowfoundry.client.FlowFoundryPlatformClient;
import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryWorker;
import org.springframework.stereotype.Component;

/** Local Activity implementation on the business Worker: forwards run events to platform HTTP API. */
@Component
@ConditionalOnFlowFoundryWorker
public class RemoteFlowRunRecorder implements FlowRunRecorder {

  private final FlowFoundryPlatformClient platformClient;

  public RemoteFlowRunRecorder(FlowFoundryPlatformClient platformClient) {
    this.platformClient = platformClient;
  }

  @Override
  public void recordEvent(FlowRunEventCommand command) {
    platformClient.recordRunEvent(command);
  }
}
