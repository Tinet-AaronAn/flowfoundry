package com.tinet.flowfoundary.interpreter.model;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Canvas-to-runtime trace metadata for Temporal UI, logs, and FlowFoundry Runtime. */
public record FlowFoundryTrace(
    String nodeId, String nodeName, String canvasKind, String activityType) {

  public static final String INPUT_KEY = "_flowFoundryTrace";
  public static final String CONFIG_KEY = "flowFoundryTrace";
  private static final int SUMMARY_MAX_BYTES = 200;

  public static FlowFoundryTrace fromNode(ExecutionNode node) {
    if (node == null) {
      return new FlowFoundryTrace(null, null, null, null);
    }
    Object rawConfig = configTrace(node.config());
    if (rawConfig instanceof Map<?, ?> configMap) {
      FlowFoundryTrace fromConfig = fromMap(configMap);
      if (fromConfig.nodeId() != null) {
        return fromConfig;
      }
    }
    return new FlowFoundryTrace(
        node.id(),
        node.id(),
        null,
        node.activityType());
  }

  public static FlowFoundryTrace fromInput(Map<String, Object> input) {
    if (input == null) {
      return new FlowFoundryTrace(null, null, null, null);
    }
    Object topLevel = input.get(INPUT_KEY);
    if (topLevel instanceof Map<?, ?> map) {
      return fromMap(map);
    }
    Object config = input.get("_config");
    if (config instanceof Map<?, ?> configMap) {
      Object rawTrace = configTrace(castMap(configMap));
      if (rawTrace instanceof Map<?, ?> traceMap) {
        return fromMap(traceMap);
      }
    }
    return new FlowFoundryTrace(null, null, null, null);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (nodeId != null) {
      map.put("nodeId", nodeId);
    }
    if (nodeName != null) {
      map.put("nodeName", nodeName);
    }
    if (canvasKind != null) {
      map.put("canvasKind", canvasKind);
    }
    if (activityType != null) {
      map.put("activityType", activityType);
    }
    return map;
  }

  public String activitySummary() {
    String label = blank(nodeName) ? nodeId : nodeName;
    String type = blank(activityType) ? "activity" : activityType;
    if (blank(label)) {
      return truncateSummary(type);
    }
    if (blank(nodeId) || label.equals(nodeId)) {
      return truncateSummary(type + " · " + label);
    }
    return truncateSummary(type + " · " + label + " (" + nodeId + ")");
  }

  public String timerSummary(String duration) {
    String label = blank(nodeName) ? nodeId : nodeName;
    String wait = blank(duration) ? "timer" : duration;
    if (blank(label)) {
      return truncateSummary("timer · " + wait);
    }
    if (blank(nodeId) || label.equals(nodeId)) {
      return truncateSummary("timer · " + wait + " · " + label);
    }
    return truncateSummary("timer · " + wait + " · " + label + " (" + nodeId + ")");
  }

  public String workflowDetailsLine() {
    String label = blank(nodeName) ? nodeId : nodeName;
    if (blank(activityType)) {
      return truncateSummary("Current: " + label);
    }
    return truncateSummary("Current: " + activityType + " · " + label);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castMap(Map<?, ?> raw) {
    return (Map<String, Object>) raw;
  }

  @SuppressWarnings("unchecked")
  private static Object configTrace(Map<String, Object> config) {
    if (config == null) {
      return null;
    }
    return config.get(CONFIG_KEY);
  }

  private static FlowFoundryTrace fromMap(Map<?, ?> raw) {
    if (raw == null || raw.isEmpty()) {
      return new FlowFoundryTrace(null, null, null, null);
    }
    return new FlowFoundryTrace(
        stringValue(raw.get("nodeId")),
        stringValue(raw.get("nodeName")),
        stringValue(raw.get("canvasKind")),
        stringValue(raw.get("activityType")));
  }

  private static String stringValue(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  public static String truncateSummary(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length <= SUMMARY_MAX_BYTES) {
      return value;
    }
    StringBuilder builder = new StringBuilder();
    int used = 0;
    for (int offset = 0; offset < value.length(); ) {
      int codePoint = value.codePointAt(offset);
      int charCount = Character.charCount(codePoint);
      String piece = value.substring(offset, offset + charCount);
      int pieceBytes = piece.getBytes(StandardCharsets.UTF_8).length;
      if (used + pieceBytes > SUMMARY_MAX_BYTES - 3) {
        break;
      }
      builder.append(piece);
      used += pieceBytes;
      offset += charCount;
    }
    return builder + "...";
  }
}
