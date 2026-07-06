package com.tinet.flowfoundry.activity;

import com.tinet.flowfoundry.interpreter.runtime.ActivityExecutionContext;
import com.tinet.flowfoundry.interpreter.runtime.DualModeActivityHandler;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Human Task activity: registers in-system work context. The interpreter waits for
 * {@code completeHumanTask} after this Activity returns.
 */
@Component
public class HumanTaskActivity extends DualModeActivityHandler {

  public Map<String, Object> execute(Map<String, Object> input) {
    Map<String, Object> safeInput = input == null ? Map.of() : input;
    Map<String, Object> config = configMap(safeInput.get("_config"));
    String mode = humanTaskMode(config);
    String nodeId = nodeId(config);
    Map<String, Object> assignment = assignment(config);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("nodeId", nodeId);
    result.put("mode", mode);
    result.put("assignment", assignment);

    if (ActivityExecutionContext.from(safeInput).usesStubActivities()) {
      result.put("taskId", "stub-human-task:" + nodeId);
      result.put("waiting", true);
      result.put("outcome", null);
      return result;
    }

    result.put("taskId", "human-task:" + nodeId);
    result.put("waiting", true);
    result.put("outcome", null);
    return result;
  }

  public static String humanTaskMode(Map<String, Object> config) {
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

  @SuppressWarnings("unchecked")
  private static Map<String, Object> configMap(Object raw) {
    if (raw instanceof Map<?, ?> map) {
      return new LinkedHashMap<>((Map<String, Object>) map);
    }
    return new LinkedHashMap<>();
  }

  private static String nodeId(Map<String, Object> config) {
    Object nodeId = config.get("nodeId");
    return nodeId == null || String.valueOf(nodeId).isBlank() ? "unknown" : String.valueOf(nodeId);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> assignment(Map<String, Object> config) {
    Object raw = config.get("flowFoundryAssignmentDefinition");
    if (raw instanceof Map<?, ?> map) {
      return new LinkedHashMap<>((Map<String, Object>) map);
    }
    return Map.of();
  }
}
