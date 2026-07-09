package com.tinet.flowfoundry.contract;

import com.tinet.flowfoundry.flow.FlowDefinition;
import com.tinet.flowfoundry.interpreter.model.ExecutionPlan;
import com.tinet.flowfoundry.interpreter.model.HumanTaskCompletion;
import java.util.Map;

/** Shared REST contract types for platform Flow API (core controllers + SDK client). */
public final class FlowApiContracts {

  private FlowApiContracts() {}

  public record RunRequest(
      FlowDefinition flow,
      String workflowId,
      String businessKey,
      String runSource,
      Map<String, Object> input) {}

  public record RunResponse(
      String workflowId,
      String runId,
      String businessKey,
      String runSource,
      ExecutionPlan executionPlan) {}

  /**
   * Start a persisted workflow version without sending FlowDefinition DSL.
   *
   * @param runWorkflowId optional Temporal execution workflow id (not the definition id)
   */
  public record StartSavedWorkflowRequest(
      Map<String, Object> input, String businessKey, String runWorkflowId) {}

  public record CompleteHumanTaskRequest(HumanTaskCompletion completion) {}
}
