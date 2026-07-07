package com.tinet.flowfoundry.api;

import com.tinet.flowfoundry.interpreter.model.HumanTaskNodeState;
import java.util.List;
import java.util.Map;

public record RunStatusResponse(
    String workflowId,
    String runId,
    String temporalStatus,
    String status,
    String flowId,
    String version,
    String businessKey,
    String runSource,
    String currentNodeId,
    String currentNodeName,
    String currentActivityType,
    String waitingHumanTaskNodeId,
    String failureMessage,
    String failureType,
    Map<String, Object> variables,
    Object lastResult,
    List<HumanTaskNodeState> humanTasks,
    List<Map<String, Object>> temporalHistory,
    String temporalNamespace,
    String temporalUiBaseUrl,
    String temporalHistoryUrl) {}
