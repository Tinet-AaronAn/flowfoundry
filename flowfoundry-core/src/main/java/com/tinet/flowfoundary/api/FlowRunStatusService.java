package com.tinet.flowfoundary.api;

import com.tinet.flowfoundary.config.TemporalProperties;
import com.tinet.flowfoundary.interpreter.FlowInterpreterWorkflow;
import com.tinet.flowfoundary.interpreter.model.HumanTaskNodeState;
import com.tinet.flowfoundary.interpreter.model.InterpreterState;
import com.tinet.flowfoundary.interpreter.model.InterpreterStatus;
import io.grpc.StatusRuntimeException;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.EventType;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionResponse;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryRequest;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.serviceclient.WorkflowServiceStubs;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FlowRunStatusService {

  private final WorkflowClient workflowClient;
  private final TemporalProperties temporalProperties;

  public FlowRunStatusService(WorkflowClient workflowClient, TemporalProperties temporalProperties) {
    this.workflowClient = workflowClient;
    this.temporalProperties = temporalProperties;
  }

  public RunStatusResponse getRunStatus(String workflowId) {
    DescribeWorkflowExecutionResponse describe = describeExecution(workflowId);
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
    if (isTerminalFailure(temporalStatus)) {
      FailureDetails failure = extractFailure(execution);
      failureMessage = failure.message();
      failureType = failure.type();
    }

    String status = resolveStatus(interpreter, temporalStatus);
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
        humanTasks);
  }

  private DescribeWorkflowExecutionResponse describeExecution(String workflowId) {
    WorkflowServiceStubs stubs = workflowClient.getWorkflowServiceStubs();
    try {
      return stubs
          .blockingStub()
          .describeWorkflowExecution(
              DescribeWorkflowExecutionRequest.newBuilder()
                  .setNamespace(temporalProperties.namespace())
                  .setExecution(WorkflowExecution.newBuilder().setWorkflowId(workflowId).build())
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

  private FailureDetails extractFailure(WorkflowExecution execution) {
    WorkflowServiceStubs stubs = workflowClient.getWorkflowServiceStubs();
    GetWorkflowExecutionHistoryResponse history =
        stubs
            .blockingStub()
            .getWorkflowExecutionHistory(
                GetWorkflowExecutionHistoryRequest.newBuilder()
                    .setNamespace(temporalProperties.namespace())
                    .setExecution(execution)
                    .build());
    List<HistoryEvent> events = history.getHistory().getEventsList();
    for (int i = events.size() - 1; i >= 0; i--) {
      HistoryEvent event = events.get(i);
      if (event.getEventType() == EventType.EVENT_TYPE_WORKFLOW_EXECUTION_FAILED) {
        return new FailureDetails(
            "WORKFLOW_FAILED",
            event.getWorkflowExecutionFailedEventAttributes().getFailure().getMessage());
      }
      if (event.getEventType() == EventType.EVENT_TYPE_ACTIVITY_TASK_FAILED) {
        return new FailureDetails(
            "ACTIVITY_FAILED",
            event.getActivityTaskFailedEventAttributes().getFailure().getMessage());
      }
    }
    return new FailureDetails("WORKFLOW_FAILED", null);
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

  private record FailureDetails(String type, String message) {}
}
