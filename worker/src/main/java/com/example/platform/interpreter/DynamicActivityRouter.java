package com.example.platform.interpreter;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import java.util.Map;

@ActivityInterface
public interface DynamicActivityRouter {

  @ActivityMethod(name = "dynamic-activity-router")
  Object execute(String activityType, Map<String, Object> input);
}
