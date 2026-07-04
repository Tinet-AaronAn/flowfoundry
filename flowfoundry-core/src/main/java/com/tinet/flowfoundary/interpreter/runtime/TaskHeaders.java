package com.tinet.flowfoundary.interpreter.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static key-value metadata attached to Activity nodes (merged from legacy canvas
 * {@code headers} into {@code config.taskHeaders}). Passed to Activities via
 * {@code input._config.taskHeaders}.
 */
public final class TaskHeaders {

  public static final String CONFIG_KEY = "taskHeaders";

  private TaskHeaders() {}

  @SuppressWarnings("unchecked")
  public static Map<String, Object> fromActivityInput(Map<String, Object> input) {
    if (input == null) {
      return Map.of();
    }
    Object rawConfig = input.get("_config");
    if (!(rawConfig instanceof Map<?, ?> config)) {
      return Map.of();
    }
    return fromConfig((Map<String, Object>) config);
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> fromConfig(Map<String, Object> config) {
    if (config == null || config.isEmpty()) {
      return Map.of();
    }
    Object raw = config.get(CONFIG_KEY);
    if (!(raw instanceof Map<?, ?> map)) {
      return Map.of();
    }
    Map<String, Object> headers = new LinkedHashMap<>();
    map.forEach((key, value) -> headers.put(String.valueOf(key), value));
    return headers;
  }
}
