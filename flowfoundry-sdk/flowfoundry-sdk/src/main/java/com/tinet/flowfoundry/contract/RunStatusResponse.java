package com.tinet.flowfoundry.contract;

import com.tinet.flowfoundry.interpreter.model.HumanTaskNodeState;
import com.tinet.flowfoundry.run.FlowRunContracts.FlowNodeRunDto;
import com.tinet.flowfoundry.run.FlowRunContracts.FlowRunEventDto;
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
    String temporalHistoryUrl,
    List<ChildWorkflowRunSummary> pendingChildWorkflows,
    List<FlowRunEventDto> executionLogs,
    List<FlowNodeRunDto> nodeRuns) {}
