package com.example.platform.interpreter.model;

import java.util.Map;

public record InterpreterState(
    String flowId,
    String version,
    String businessKey,
    InterpreterStatus status,
    String currentNodeId,
    String waitingHumanTaskNodeId,
    Map<String, Object> variables,
    Object lastResult) {}
