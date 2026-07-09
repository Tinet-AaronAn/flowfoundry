package com.tinet.flowfoundry.activity;

import java.util.Map;
import java.util.Set;

/** Core activity types implemented in flowfoundry-core (not business modules). */
public final class ActivityTypes {

  public static final String PLATFORM_TASK_QUEUE = "flowfoundry-platform";

  public static final String SCRIPT_RUNTIME = "script-runtime";

  public static final String HUMAN_TASK = "human-task";

  private static final Set<String> CORE_TYPES =
      Set.of(SCRIPT_RUNTIME, HUMAN_TASK);

  private ActivityTypes() {}

  public static boolean isCore(String activityType) {
    return activityType != null && CORE_TYPES.contains(activityType);
  }

  public static boolean isHumanTaskActivity(String activityType) {
    return activityType != null && HUMAN_TASK.equals(activityType);
  }

  public static String normalize(String activityType) {
    return activityType;
  }

  /** Human Task mode from node config (managed / offline). */
  public static String humanTaskMode(Map<String, Object> config) {
    if (config == null) {
      return "managed";
    }
    Object raw = config.get("flowFoundryHumanTask");
    if (raw instanceof Map<?, ?> map) {
      Object mode = map.get("mode");
      if (mode != null && !String.valueOf(mode).isBlank()) {
        String normalized = String.valueOf(mode).trim();
        if ("offline".equalsIgnoreCase(normalized)) {
          return "managed";
        }
        return normalized;
      }
    }
    return "managed";
  }
}
