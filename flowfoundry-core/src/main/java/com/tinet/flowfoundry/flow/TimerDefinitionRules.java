package com.tinet.flowfoundry.flow;

import java.util.Map;

/** Validates timer definitions for Start vs Intermediate events. */
public final class TimerDefinitionRules {

  private TimerDefinitionRules() {}

  public static void validateIntermediate(Map<String, Object> config, String nodeId) {
    String type = timerType(config);
    if ("cycle".equals(type)) {
      throw new IllegalArgumentException(
          "Intermediate Event does not support timer type 'cycle'. "
              + "Use Start Event (Timer Start) for periodic scheduling, or duration/date here: "
              + nodeId);
    }
  }

  public static void validateStart(Map<String, Object> config, String nodeId) {
    String subtype = startSubtype(config);
    if (!"timer".equals(subtype)) {
      return;
    }
    Map<String, Object> timerDefinition = timerDefinition(config);
    if (timerDefinition == null || timerDefinition.isEmpty()) {
      throw new IllegalArgumentException(
          "Timer Start requires config.timerDefinition on Start Event: " + nodeId);
    }
    String type = timerType(config);
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException(
          "Timer Start requires timerDefinition.type cycle or date: " + nodeId);
    }
    if ("duration".equals(type)) {
      throw new IllegalArgumentException(
          "Timer Start does not support duration; use cycle or date: " + nodeId);
    }
    if (!"cycle".equals(type) && !"date".equals(type)) {
      throw new IllegalArgumentException(
          "Timer Start supports timerDefinition.type cycle or date only: " + nodeId);
    }
    Object value = timerDefinition.get("value");
    if (value == null || String.valueOf(value).isBlank()) {
      throw new IllegalArgumentException("Timer Start requires timerDefinition.value: " + nodeId);
    }
  }

  public static boolean isTimerStart(Map<String, Object> config) {
    return "timer".equals(startSubtype(config));
  }

  public static String startSubtype(Map<String, Object> config) {
    if (config == null) {
      return "none";
    }
    Object raw = config.get("startEventSubtype");
    if (raw == null || String.valueOf(raw).isBlank()) {
      return "none";
    }
    return String.valueOf(raw).trim().toLowerCase();
  }

  public static String timerType(Map<String, Object> config) {
    Map<String, Object> timerDefinition = timerDefinition(config);
    if (timerDefinition == null) {
      return "duration";
    }
    Object raw = timerDefinition.get("type");
    if (raw == null || String.valueOf(raw).isBlank()) {
      return "duration";
    }
    return String.valueOf(raw).trim().toLowerCase();
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> timerDefinition(Map<String, Object> config) {
    if (config == null) {
      return null;
    }
    Object raw = config.get("timerDefinition");
    if (raw instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    return null;
  }
}
