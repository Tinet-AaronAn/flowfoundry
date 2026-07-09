package com.tinet.flowfoundry.run;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface FlowRunRecorder {

  @ActivityMethod
  void recordEvent(FlowRunEventCommand command);
}
