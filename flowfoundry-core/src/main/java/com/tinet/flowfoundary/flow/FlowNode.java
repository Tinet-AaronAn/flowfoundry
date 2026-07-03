package com.tinet.flowfoundary.flow;

import java.util.List;
import java.util.Map;

public record FlowNode(
    String id,
    String kind,
    String name,
    String canvasKind,
    String activityType,
    String taskQueue,
    String timeout,
    Integer maxAttempts,
    String decisionRef,
    String decisionVersion,
    List<String> inputArgs,
    Map<String, String> inputMapping,
    Map<String, String> outputMapping,
    Map<String, Object> config) {}
