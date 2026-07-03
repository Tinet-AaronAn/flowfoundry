package com.tinet.flowfoundary.interpreter.model;

import java.util.Map;

public record HumanTaskCompletion(
    String nodeId,
    String outcome,
    Map<String, Object> variables) {}
