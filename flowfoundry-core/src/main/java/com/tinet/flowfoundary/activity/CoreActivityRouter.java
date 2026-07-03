package com.tinet.flowfoundary.activity;

import java.util.Map;
import org.springframework.stereotype.Component;

/** Routes core activities that ship with flowfoundry-core. */
@Component
public class CoreActivityRouter {

  private final ScriptRuntimeActivity scriptRuntimeActivity;
  private final HumanTaskActivity humanTaskActivity;

  public CoreActivityRouter(
      ScriptRuntimeActivity scriptRuntimeActivity, HumanTaskActivity humanTaskActivity) {
    this.scriptRuntimeActivity = scriptRuntimeActivity;
    this.humanTaskActivity = humanTaskActivity;
  }

  public boolean supports(String activityType) {
    return ActivityTypes.isCore(activityType);
  }

  public Object execute(String activityType, Map<String, Object> input) {
    String normalized = ActivityTypes.normalize(activityType);
    if (ActivityTypes.SCRIPT_RUNTIME.equals(normalized)) {
      return scriptRuntimeActivity.execute(input);
    }
    if (ActivityTypes.HUMAN_TASK.equals(activityType)) {
      return humanTaskActivity.execute(input);
    }
    throw new IllegalArgumentException("Unsupported core activity type: " + activityType);
  }
}
