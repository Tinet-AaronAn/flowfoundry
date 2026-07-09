package com.tinet.flowfoundry.contract;

import com.tinet.flowfoundry.interpreter.model.HumanTaskNodeState;
import java.util.List;

/** Runtime snapshot of a Temporal child workflow still awaited by the parent run. */
public record ChildWorkflowRunSummary(
    String workflowId,
    String runId,
    String temporalStatus,
    String status,
    String flowId,
    String businessKey,
    String parentNodeId,
    String currentNodeId,
    String currentNodeName,
    String currentActivityType,
    String waitingHumanTaskNodeId,
    List<HumanTaskNodeState> humanTasks,
    String temporalHistoryUrl) {}
