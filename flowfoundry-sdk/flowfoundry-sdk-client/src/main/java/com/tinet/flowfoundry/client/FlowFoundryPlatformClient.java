package com.tinet.flowfoundry.client;

import com.tinet.flowfoundry.contract.ChildWorkflowRunSummary;
import com.tinet.flowfoundry.contract.FlowApiContracts.RunRequest;
import com.tinet.flowfoundry.contract.FlowApiContracts.RunResponse;
import com.tinet.flowfoundry.contract.FlowApiContracts.StartSavedWorkflowRequest;
import com.tinet.flowfoundry.contract.RunStatusResponse;
import com.tinet.flowfoundry.flow.FlowDefinition;
import com.tinet.flowfoundry.interpreter.model.ExecutionPlan;
import com.tinet.flowfoundry.interpreter.model.HumanTaskCompletion;
import com.tinet.flowfoundry.registry.ActivityRegistry;
import com.tinet.flowfoundry.run.FlowRunContracts.FlowRunListPage;
import com.tinet.flowfoundry.workflow.NamespaceContextDto;
import com.tinet.flowfoundry.workflow.WorkflowContracts.AllocateIdRequest;
import com.tinet.flowfoundry.workflow.WorkflowContracts.AllocateIdResponse;
import com.tinet.flowfoundry.workflow.WorkflowContracts.CreateWorkflowRequest;
import com.tinet.flowfoundry.workflow.WorkflowContracts.CreateWorkflowVersionRequest;
import com.tinet.flowfoundry.workflow.WorkflowContracts.SaveWorkflowVersionRequest;
import com.tinet.flowfoundry.workflow.WorkflowContracts.UpdateWorkflowRequest;
import com.tinet.flowfoundry.workflow.WorkflowContracts.WorkflowRecordDto;
import com.tinet.flowfoundry.workflow.WorkflowContracts.WorkflowVersionDto;
import java.util.List;
import java.util.Map;

/** Typed HTTP client for flowfoundry-core platform REST API. */
public interface FlowFoundryPlatformClient {

  Map<String, Object> getPublicConfig();

  NamespaceContextDto getWorkflowContext();

  List<WorkflowRecordDto> listWorkflows(String keyword, String status);

  WorkflowRecordDto getWorkflow(String workflowId);

  WorkflowVersionDto getWorkflowVersion(String workflowId, String version);

  WorkflowRecordDto createWorkflow(CreateWorkflowRequest request);

  WorkflowRecordDto saveWorkflowVersion(
      String workflowId, String version, SaveWorkflowVersionRequest request);

  WorkflowRecordDto createWorkflowVersion(String workflowId, CreateWorkflowVersionRequest request);

  WorkflowRecordDto updateWorkflow(String workflowId, UpdateWorkflowRequest request);

  void deleteWorkflow(String workflowId);

  AllocateIdResponse allocateId(AllocateIdRequest request);

  Map<String, Object> idKinds();

  ActivityRegistry listActivities();

  ExecutionPlan compileFlow(FlowDefinition definition);

  RunResponse runFlow(RunRequest request);

  /** Start a persisted active workflow version (platform loads DSL from storage). */
  RunResponse startWorkflowVersion(
      String workflowId, String version, StartSavedWorkflowRequest request);

  FlowRunListPage listRuns(String keyword, int page, int size);

  RunStatusResponse getRunStatus(String workflowId);

  void completeHumanTask(String workflowId, HumanTaskCompletion completion);

  /** @deprecated exposed for advanced clients; prefer typed methods above */
  default List<ChildWorkflowRunSummary> unsupportedChildSummaries() {
    return List.of();
  }
}
