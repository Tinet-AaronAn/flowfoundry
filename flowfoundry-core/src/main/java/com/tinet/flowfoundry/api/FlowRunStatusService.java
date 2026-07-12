package com.tinet.flowfoundry.api;

import com.tinet.flowfoundry.config.TemporalProperties;
import com.tinet.flowfoundry.contract.ChildWorkflowRunSummary;
import com.tinet.flowfoundry.contract.RunStatusResponse;
import com.tinet.flowfoundry.run.FlowRunContracts.FlowNodeRunDto;
import com.tinet.flowfoundry.run.FlowRunContracts.FlowRunEventDto;
import com.tinet.flowfoundry.run.FlowRunService;
import com.tinet.flowfoundry.temporal.RunNamespaceLocator;
import com.tinet.flowfoundry.temporal.TemporalConnectionRegistry;
import com.tinet.flowfoundry.interpreter.FlowInterpreterWorkflow;
import com.tinet.flowfoundry.interpreter.model.HumanTaskNodeState;
import com.tinet.flowfoundry.interpreter.model.InterpreterState;
import com.tinet.flowfoundry.interpreter.model.InterpreterStatus;
import io.grpc.StatusRuntimeException;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.api.workflow.v1.PendingChildExecutionInfo;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionResponse;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryRequest;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.serviceclient.WorkflowServiceStubs;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

@Service
public class FlowRunStatusService {

  private final TemporalConnectionRegistry connectionRegistry;
  private final RunNamespaceLocator runNamespaceLocator;
  private final TemporalProperties temporalProperties;
  private final FlowRunService flowRunService;

  public FlowRunStatusService(
      TemporalConnectionRegistry connectionRegistry,
      RunNamespaceLocator runNamespaceLocator,
      TemporalProperties temporalProperties,
      FlowRunService flowRunService) {
    this.connectionRegistry = connectionRegistry;
    this.runNamespaceLocator = runNamespaceLocator;
    this.temporalProperties = temporalProperties;
    this.flowRunService = flowRunService;
  }

  public RunStatusResponse getRunStatus(String workflowId) {
    String namespace = runNamespaceLocator.locate(workflowId);
    WorkflowClient workflowClient =
        connectionRegistry.clientsForPlatformNamespace(namespace).workflowClient(namespace);
    DescribeWorkflowExecutionResponse describe = describeExecution(namespace, workflowId);
    WorkflowExecution execution = describe.getWorkflowExecutionInfo().getExecution();
    WorkflowExecutionStatus temporalStatus = describe.getWorkflowExecutionInfo().getStatus();
    String temporalStatusName = temporalStatus.name();

    InterpreterState interpreter = null;
    List<HumanTaskNodeState> humanTasks = List.of();
    try {
      FlowInterpreterWorkflow stub =
          workflowClient.newWorkflowStub(FlowInterpreterWorkflow.class, workflowId);
      interpreter = stub.getState();
      humanTasks = safeHumanTasks(stub.getHumanTasks());
    } catch (RuntimeException ignored) {
      // Query unavailable for closed or failed executions — fall back to Temporal metadata.
    }

    String failureMessage = null;
    String failureType = null;
    List<Map<String, Object>> temporalHistory = List.of();
    List<HistoryEvent> historyEvents = fetchHistoryEvents(namespace, execution);
    temporalHistory = TemporalHistoryFormatter.format(historyEvents);
    if (isTerminalFailure(temporalStatus)) {
      TemporalHistoryFormatter.FailureDetails failure =
          TemporalHistoryFormatter.extractFailure(historyEvents);
      failureMessage = failure.message();
      failureType = failure.type();
    }

    String status = resolveStatus(interpreter, temporalStatus);
    String uiBaseUrl = connectionRegistry.uiBaseUrlForPlatformNamespace(namespace);
    String historyUrl = buildTemporalHistoryUrl(namespace, uiBaseUrl, workflowId, execution.getRunId());
    List<ChildWorkflowRunSummary> pendingChildWorkflows =
        summarizePendingChildWorkflows(describe, namespace, uiBaseUrl);
    flowRunService.syncRunStatus(
        workflowId,
        execution.getRunId(),
        temporalStatusName,
        status,
        failureMessage,
        failureType);
    List<FlowRunEventDto> executionLogs = flowRunService.listEvents(workflowId);
    List<FlowNodeRunDto> nodeRuns = flowRunService.listNodeRuns(workflowId);
    return new RunStatusResponse(
        workflowId,
        execution.getRunId(),
        temporalStatusName,
        status,
        interpreter == null ? null : interpreter.flowId(),
        interpreter == null ? null : interpreter.version(),
        interpreter == null ? null : interpreter.businessKey(),
        interpreter == null ? null : interpreter.runSource(),
        interpreter == null ? null : interpreter.currentNodeId(),
        interpreter == null ? null : interpreter.currentNodeName(),
        interpreter == null ? null : interpreter.currentActivityType(),
        interpreter == null ? null : interpreter.waitingHumanTaskNodeId(),
        failureMessage,
        failureType,
        interpreter == null ? null : interpreter.variables(),
        interpreter == null ? null : interpreter.lastResult(),
        humanTasks,
        temporalHistory,
        namespace,
        uiBaseUrl,
        historyUrl,
        pendingChildWorkflows,
        executionLogs,
        nodeRuns);
  }

  private List<ChildWorkflowRunSummary> summarizePendingChildWorkflows(
      DescribeWorkflowExecutionResponse describe, String namespace, String uiBaseUrl) {
    List<ChildWorkflowRunSummary> summaries = new ArrayList<>();
    for (PendingChildExecutionInfo pending : describe.getPendingChildrenList()) {
      summaries.add(
          summarizeChildWorkflow(
              pending.getWorkflowId(), pending.getRunId(), namespace, uiBaseUrl));
    }
    return summaries;
  }

  private ChildWorkflowRunSummary summarizeChildWorkflow(
      String workflowId, String runId, String namespace, String uiBaseUrl) {
    try {
      DescribeWorkflowExecutionResponse describe =
          describeExecution(
              namespace,
              workflowId,
              runId == null || runId.isBlank()
                  ? null
                  : WorkflowExecution.newBuilder()
                      .setWorkflowId(workflowId)
                      .setRunId(runId)
                      .build());
      WorkflowExecution execution = describe.getWorkflowExecutionInfo().getExecution();
      WorkflowExecutionStatus temporalStatus = describe.getWorkflowExecutionInfo().getStatus();
      InterpreterState interpreter = null;
      List<HumanTaskNodeState> humanTasks = List.of();
      try {
        FlowInterpreterWorkflow stub =
            connectionRegistry
                .clientsForPlatformNamespace(namespace)
                .workflowClient(namespace)
                .newWorkflowStub(FlowInterpreterWorkflow.class, workflowId);
        interpreter = stub.getState();
        humanTasks = safeHumanTasks(stub.getHumanTasks());
      } catch (RuntimeException ignored) {
        // Child may be starting — fall back to Temporal metadata only.
      }
      String businessKey = interpreter == null ? null : interpreter.businessKey();
      return new ChildWorkflowRunSummary(
          workflowId,
          execution.getRunId(),
          temporalStatus.name(),
          resolveStatus(interpreter, temporalStatus),
          interpreter == null ? null : interpreter.flowId(),
          businessKey,
          parentNodeIdFromBusinessKey(businessKey),
          interpreter == null ? null : interpreter.currentNodeId(),
          interpreter == null ? null : interpreter.currentNodeName(),
          interpreter == null ? null : interpreter.currentActivityType(),
          interpreter == null ? null : interpreter.waitingHumanTaskNodeId(),
          humanTasks,
          buildTemporalHistoryUrl(namespace, uiBaseUrl, workflowId, execution.getRunId()));
    } catch (RuntimeException e) {
      return new ChildWorkflowRunSummary(
          workflowId,
          runId,
          null,
          InterpreterStatus.RUNNING.name(),
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          List.of(),
          buildTemporalHistoryUrl(namespace, uiBaseUrl, workflowId, runId));
    }
  }

  static String parentNodeIdFromBusinessKey(String businessKey) {
    if (businessKey == null || businessKey.isBlank()) {
      return null;
    }
    int slash = businessKey.lastIndexOf('/');
    if (slash < 0 || slash >= businessKey.length() - 1) {
      return null;
    }
    return businessKey.substring(slash + 1);
  }

  static String buildTemporalHistoryUrl(
      String namespace, String uiBaseUrl, String workflowId, String runId) {
    if (workflowId == null || workflowId.isBlank()) {
      return null;
    }
    String ns = encodePathSegment(namespace == null || namespace.isBlank() ? "default" : namespace);
    String wf = encodePathSegment(workflowId);
    String base =
        uiBaseUrl == null || uiBaseUrl.isBlank()
            ? "http://127.0.0.1:8080"
            : uiBaseUrl.trim().replaceAll("/+$", "");
    if (runId == null || runId.isBlank()) {
      return base + "/namespaces/" + ns + "/workflows/" + wf + "/history";
    }
    return base
        + "/namespaces/"
        + ns
        + "/workflows/"
        + wf
        + "/"
        + encodePathSegment(runId)
        + "/history";
  }

  private static String encodePathSegment(String value) {
    return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
  }

  private List<HistoryEvent> fetchHistoryEvents(String namespace, WorkflowExecution execution) {
    WorkflowServiceStubs stubs =
        connectionRegistry.clientsForPlatformNamespace(namespace).serviceStubs();
    GetWorkflowExecutionHistoryResponse history =
        stubs
            .blockingStub()
            .getWorkflowExecutionHistory(
                GetWorkflowExecutionHistoryRequest.newBuilder()
                    .setNamespace(namespace)
                    .setExecution(execution)
                    .build());
    return history.getHistory().getEventsList();
  }

  private DescribeWorkflowExecutionResponse describeExecution(String namespace, String workflowId) {
    return describeExecution(namespace, workflowId, null);
  }

  private DescribeWorkflowExecutionResponse describeExecution(
      String namespace, String workflowId, WorkflowExecution executionHint) {
    WorkflowServiceStubs stubs =
        connectionRegistry.clientsForPlatformNamespace(namespace).serviceStubs();
    WorkflowExecution execution =
        executionHint != null
            ? executionHint
            : WorkflowExecution.newBuilder().setWorkflowId(workflowId).build();
    try {
      return stubs
          .blockingStub()
          .describeWorkflowExecution(
              DescribeWorkflowExecutionRequest.newBuilder()
                  .setNamespace(namespace)
                  .setExecution(execution)
                  .build());
    } catch (StatusRuntimeException e) {
      if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
        throw new WorkflowNotFoundException(
            WorkflowExecution.newBuilder().setWorkflowId(workflowId).build(),
            FlowInterpreterWorkflow.class.getSimpleName(),
            e);
      }
      throw e;
    }
  }

  private static List<HumanTaskNodeState> safeHumanTasks(List<HumanTaskNodeState> humanTasks) {
    return humanTasks == null ? List.of() : humanTasks;
  }

  private static String resolveStatus(
      InterpreterState interpreter, WorkflowExecutionStatus temporalStatus) {
    if (interpreter != null && interpreter.status() != null) {
      return interpreter.status().name();
    }
    return switch (temporalStatus) {
      case WORKFLOW_EXECUTION_STATUS_COMPLETED -> InterpreterStatus.COMPLETED.name();
      case WORKFLOW_EXECUTION_STATUS_FAILED -> InterpreterStatus.FAILED.name();
      case WORKFLOW_EXECUTION_STATUS_CANCELED -> "CANCELED";
      case WORKFLOW_EXECUTION_STATUS_TERMINATED -> "TERMINATED";
      case WORKFLOW_EXECUTION_STATUS_TIMED_OUT -> "TIMED_OUT";
      default -> InterpreterStatus.RUNNING.name();
    };
  }

  private static boolean isTerminalFailure(WorkflowExecutionStatus status) {
    return status == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_FAILED
        || status == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_TIMED_OUT
        || status == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_TERMINATED;
  }
}
