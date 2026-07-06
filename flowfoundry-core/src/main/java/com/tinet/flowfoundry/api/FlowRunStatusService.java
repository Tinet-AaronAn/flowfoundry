package com.tinet.flowfoundry.api;

import com.tinet.flowfoundry.config.TemporalProperties;
import com.tinet.flowfoundry.interpreter.FlowInterpreterWorkflow;
import com.tinet.flowfoundry.interpreter.model.HumanTaskNodeState;
import com.tinet.flowfoundry.interpreter.model.InterpreterState;
import com.tinet.flowfoundry.interpreter.model.InterpreterStatus;
import io.grpc.StatusRuntimeException;
import io.temporal.api.common.v1.WorkflowExecution;
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
import java.util.Map;
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
    List<Map<String, Object>> temporalHistory = List.of();
    List<HistoryEvent> historyEvents = fetchHistoryEvents(execution);
    temporalHistory = TemporalHistoryFormatter.format(historyEvents);
    if (isTerminalFailure(temporalStatus)) {
      TemporalHistoryFormatter.FailureDetails failure =
          TemporalHistoryFormatter.extractFailure(historyEvents);
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
        humanTasks,
        temporalHistory);
  }

  private List<HistoryEvent> fetchHistoryEvents(WorkflowExecution execution) {
    WorkflowServiceStubs stubs = workflowClient.getWorkflowServiceStubs();
    GetWorkflowExecutionHistoryResponse history =
        stubs
            .blockingStub()
            .getWorkflowExecutionHistory(
                GetWorkflowExecutionHistoryRequest.newBuilder()
                    .setNamespace(temporalProperties.namespace())
                    .setExecution(execution)
                    .build());
    return history.getHistory().getEventsList();
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
