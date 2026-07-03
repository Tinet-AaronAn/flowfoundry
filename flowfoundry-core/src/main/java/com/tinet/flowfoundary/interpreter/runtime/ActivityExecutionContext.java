package com.tinet.flowfoundary.interpreter.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

public record ActivityExecutionContext(
    RunSource runSource, String businessKey, String workflowId) {

  public static final String CONTEXT_KEY = "_executionContext";

  public boolean usesStubActivities() {
    return runSource.usesStubActivities();
  }

  public static ActivityExecutionContext from(Map<String, Object> input) {
    if (input == null) {
      return new ActivityExecutionContext(RunSource.PRODUCTION, null, null);
    }
    Object raw = input.get(CONTEXT_KEY);
    if (!(raw instanceof Map<?, ?> map)) {
      return new ActivityExecutionContext(RunSource.PRODUCTION, null, null);
    }
    Object runSource = map.get("runSource");
    Object businessKey = map.get("businessKey");
    Object workflowId = map.get("workflowId");
    return new ActivityExecutionContext(
        RunSource.fromWire(runSource == null ? null : String.valueOf(runSource)),
        businessKey == null ? null : String.valueOf(businessKey),
        workflowId == null ? null : String.valueOf(workflowId));
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("runSource", runSource.wireValue());
    if (businessKey != null) {
      map.put("businessKey", businessKey);
    }
    if (workflowId != null) {
      map.put("workflowId", workflowId);
    }
    return map;
  }
}
