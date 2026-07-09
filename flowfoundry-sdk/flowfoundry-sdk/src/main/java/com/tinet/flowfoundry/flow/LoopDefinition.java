package com.tinet.flowfoundry.flow;

import java.util.Map;

/** Parsed {@code config.flowFoundryLoop} for ACTIVITY nodes. */
public record LoopDefinition(
    String mode,
    Object condition,
    int maxIterations,
    String collection,
    String elementVar,
    String indexVar,
    String iterationVar,
    boolean sequential) {

  public static final String CONFIG_KEY = "flowFoundryLoop";

  public boolean isEnabled() {
    return mode != null && !mode.isBlank() && !"none".equalsIgnoreCase(mode);
  }

  public boolean isStandard() {
    return "standard".equalsIgnoreCase(mode);
  }

  public boolean isMultiInstance() {
    return "multiInstance".equalsIgnoreCase(mode);
  }

  @SuppressWarnings("unchecked")
  public static LoopDefinition fromConfig(Map<String, Object> config) {
    if (config == null || !config.containsKey(CONFIG_KEY)) {
      return disabled();
    }
    Object raw = config.get(CONFIG_KEY);
    if (!(raw instanceof Map<?, ?> map)) {
      return disabled();
    }
    Map<String, Object> loop = (Map<String, Object>) map;
    String mode = stringValue(loop.get("mode"));
    if (mode == null || mode.isBlank() || "none".equalsIgnoreCase(mode)) {
      return disabled();
    }
    int maxIterations = 100;
    Object maxRaw = loop.get("maxIterations");
    if (maxRaw instanceof Number number) {
      maxIterations = number.intValue();
    } else if (maxRaw != null && !String.valueOf(maxRaw).isBlank()) {
      maxIterations = Integer.parseInt(String.valueOf(maxRaw).trim());
    }
    boolean sequential = true;
    Object sequentialRaw = loop.get("sequential");
    if (sequentialRaw instanceof Boolean bool) {
      sequential = bool;
    } else if (sequentialRaw != null) {
      sequential = Boolean.parseBoolean(String.valueOf(sequentialRaw));
    }
    return new LoopDefinition(
        mode,
        loop.get("condition"),
        maxIterations,
        stringValue(loop.get("collection")),
        defaultString(loop.get("elementVar"), "loop.item"),
        defaultString(loop.get("indexVar"), "loop.index"),
        defaultString(loop.get("iterationVar"), "loop.iteration"),
        sequential);
  }

  public static LoopDefinition disabled() {
    return new LoopDefinition("none", null, 0, null, null, null, null, true);
  }

  public void validate(String nodeId) {
    if (!isEnabled()) {
      return;
    }
    if (isStandard()) {
      if (condition == null || String.valueOf(condition).isBlank()) {
        throw new IllegalArgumentException(
            "Standard loop requires flowFoundryLoop.condition on node: " + nodeId);
      }
    }
    if (isMultiInstance()) {
      if (collection == null || collection.isBlank()) {
        throw new IllegalArgumentException(
            "Multi-instance loop requires flowFoundryLoop.collection on node: " + nodeId);
      }
    }
    if (maxIterations < 1 || maxIterations > 10_000) {
      throw new IllegalArgumentException(
          "flowFoundryLoop.maxIterations must be between 1 and 10000 on node: " + nodeId);
    }
    if (isMultiInstance() && !sequential) {
      throw new IllegalArgumentException(
          "Parallel multi-instance loop is not yet supported on node: " + nodeId);
    }
  }

  private static String stringValue(Object raw) {
    if (raw == null) {
      return null;
    }
    String value = String.valueOf(raw).trim();
    return value.isEmpty() ? null : value;
  }

  private static String defaultString(Object raw, String fallback) {
    String value = stringValue(raw);
    return value == null ? fallback : value;
  }
}
