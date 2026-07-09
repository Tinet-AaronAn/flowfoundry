package com.tinet.flowfoundry.run;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnFlowFoundryPlatform
public class FlowRunRecorderImpl implements FlowRunRecorder {

  private final FlowRunService flowRunService;

  public FlowRunRecorderImpl(FlowRunService flowRunService) {
    this.flowRunService = flowRunService;
  }

  @Override
  public void recordEvent(FlowRunEventCommand command) {
    flowRunService.recordEvent(command);
  }
}
