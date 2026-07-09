package com.tinet.flowfoundry.interpreter.runtime;

import com.tinet.flowfoundry.activity.ActivityTypes;
import com.tinet.flowfoundry.interpreter.model.ExecutionNode;
import com.tinet.flowfoundry.interpreter.model.ExecutionPlan;
import com.tinet.flowfoundry.interpreter.model.FlowFoundryTrace;
import com.tinet.flowfoundry.interpreter.model.InterpreterStatus;
import com.tinet.flowfoundry.interpreter.model.NodeKind;
import com.tinet.flowfoundry.run.FlowRunEventCommand;
import com.tinet.flowfoundry.run.FlowRunEventType;
import com.tinet.flowfoundry.run.FlowRunJson;
import com.tinet.flowfoundry.run.FlowRunRecorder;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Emits FlowFoundry-scoped run events via platform Temporal Activity. */
public final class WorkflowRunEventEmitter {

  private final String workflowId;
  private final String businessKey;
  private final String namespace;
  private final String runSource;
  private final ExecutionPlan plan;
  private int sequence;

  public WorkflowRunEventEmitter(
      String workflowId, String businessKey, String runSource, ExecutionPlan plan) {
    this.workflowId = workflowId;
    this.businessKey = businessKey;
    this.namespace = FlowRunJson.namespaceFromBusinessKey(businessKey);
    this.runSource = runSource;
    this.plan = plan;
  }

  public void workflowStarted() {
    emit(
        FlowRunEventType.WORKFLOW_STARTED,
        null,
        InterpreterStatus.RUNNING.name(),
        Map.of(
            "flowId", plan == null ? null : plan.flowId(),
            "version", plan == null ? null : plan.version()));
  }

  public void workflowCompleted() {
    emitTerminal(
        FlowRunEventType.WORKFLOW_COMPLETED,
        InterpreterStatus.COMPLETED.name(),
        null,
        null);
  }

  public void workflowFailed(String failureMessage, String failureType) {
    emitTerminal(
        FlowRunEventType.WORKFLOW_FAILED,
        InterpreterStatus.FAILED.name(),
        failureMessage,
        failureType);
  }

  public void nodeEntered(ExecutionNode node) {
    emit(
        FlowRunEventType.NODE_ENTERED,
        node,
        "RUNNING",
        Map.of("kind", nodeKind(node)));
  }

  public void nodeCompleted(ExecutionNode node, Map<String, Object> detail) {
    emit(FlowRunEventType.NODE_COMPLETED, node, "COMPLETED", detail);
  }

  public void nodeFailed(ExecutionNode node, String message) {
    emit(
        FlowRunEventType.NODE_FAILED,
        node,
        "FAILED",
        message == null ? Map.of() : Map.of("message", message));
  }

  public void gatewayRouted(ExecutionNode node, List<String> targetNodeIds) {
    emit(
        FlowRunEventType.GATEWAY_ROUTED,
        node,
        "ROUTED",
        Map.of("targets", targetNodeIds));
  }

  public void timerWaiting(ExecutionNode node, long durationMs) {
    emit(
        FlowRunEventType.TIMER_WAITING,
        node,
        "WAITING",
        Map.of("durationMs", durationMs));
  }

  public void timerFired(ExecutionNode node) {
    emit(FlowRunEventType.TIMER_FIRED, node, "COMPLETED", Map.of());
  }

  public void humanTaskWaiting(ExecutionNode node) {
    emit(FlowRunEventType.HUMAN_TASK_WAITING, node, "WAITING", Map.of());
  }

  public void humanTaskCompleted(ExecutionNode node, String outcome) {
    emit(
        FlowRunEventType.HUMAN_TASK_COMPLETED,
        node,
        "COMPLETED",
        outcome == null ? Map.of() : Map.of("outcome", outcome));
  }

  public void childWorkflowStarted(ExecutionNode node, String childWorkflowId) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("childWorkflowId", childWorkflowId);
    String childName = childWorkflowDisplayName(node);
    if (childName != null) {
      detail.put("childWorkflowName", childName);
    }
    emit(FlowRunEventType.CHILD_WORKFLOW_STARTED, node, "RUNNING", detail);
  }

  public void childWorkflowCompleted(ExecutionNode node, Object resultSummary) {
    Map<String, Object> detail = new LinkedHashMap<>();
    String childName = childWorkflowDisplayName(node);
    if (childName != null) {
      detail.put("childWorkflowName", childName);
    }
    if (resultSummary != null) {
      detail.put("result", resultSummary);
    }
    emit(FlowRunEventType.CHILD_WORKFLOW_COMPLETED, node, "COMPLETED", detail);
  }

  private void emitTerminal(
      FlowRunEventType eventType,
      String runStatus,
      String failureMessage,
      String failureType) {
    int seq = nextSequence();
    FlowRunEventCommand command =
        new FlowRunEventCommand(
            workflowId,
            Workflow.getInfo().getRunId(),
            namespace,
            seq,
            eventType.name(),
            null,
            null,
            null,
            null,
            null,
            null,
            Workflow.currentTimeMillis(),
            runStatus,
            plan == null ? null : plan.flowId(),
            plan == null ? null : plan.version(),
            businessKey,
            runSource,
            null,
            failureMessage,
            failureType);
    dispatch(command);
  }

  private void emit(
      FlowRunEventType eventType,
      ExecutionNode node,
      String status,
      Map<String, Object> detail) {
    FlowFoundryTrace trace = node == null ? null : FlowFoundryTrace.fromNode(node);
    int seq = nextSequence();
    FlowRunEventCommand command =
        new FlowRunEventCommand(
            workflowId,
            Workflow.getInfo().getRunId(),
            namespace,
            seq,
            eventType.name(),
            node == null ? null : node.id(),
            trace == null ? null : trace.nodeName(),
            node == null ? null : nodeKind(node),
            trace == null ? null : trace.activityType(),
            status,
            FlowRunJson.toJson(detail),
            Workflow.currentTimeMillis(),
            null,
            plan == null ? null : plan.flowId(),
            plan == null ? null : plan.version(),
            businessKey,
            runSource,
            null,
            null,
            null);
    dispatch(command);
  }

  private int nextSequence() {
    return ++sequence;
  }

  private void dispatch(FlowRunEventCommand command) {
    Async.procedure(
        () -> {
          try {
            recorder().recordEvent(command);
          } catch (Exception e) {
            Workflow.getLogger(WorkflowRunEventEmitter.class)
                .warn("Failed to record flow run event {}", command.eventType(), e);
          }
        });
  }

  private static FlowRunRecorder recorder() {
    return Workflow.newActivityStub(
        FlowRunRecorder.class,
        ActivityOptions.newBuilder()
            .setTaskQueue(ActivityTypes.PLATFORM_TASK_QUEUE)
            .setStartToCloseTimeout(Duration.ofSeconds(15))
            .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(5).build())
            .build());
  }

  private static String nodeKind(ExecutionNode node) {
    if (node == null) {
      return null;
    }
    NodeKind kind = node.requiredKind();
    return kind == null ? null : kind.name();
  }

  private static String childWorkflowDisplayName(ExecutionNode node) {
    if (node == null || node.config() == null) {
      return null;
    }
    Object name = node.config().get("childWorkflowName");
    if (name != null && !String.valueOf(name).isBlank()) {
      return String.valueOf(name).trim();
    }
    Object id = node.config().get("childWorkflowId");
    if (id != null && !String.valueOf(id).isBlank()) {
      return String.valueOf(id).trim();
    }
    return null;
  }
}
