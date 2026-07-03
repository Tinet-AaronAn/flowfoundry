package com.tinet.flowfoundary.interpreter.model;

import java.util.Map;

public record InterpreterState(
    String flowId,
    String version,
    String businessKey,
    String runSource,
    InterpreterStatus status,
    String currentNodeId,
    String waitingHumanTaskNodeId,
    Map<String, Object> variables,
    Object lastResult) {}
