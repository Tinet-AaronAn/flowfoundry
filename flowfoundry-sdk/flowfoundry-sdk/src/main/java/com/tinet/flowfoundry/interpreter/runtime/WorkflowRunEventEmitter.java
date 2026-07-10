package com.tinet.flowfoundry.interpreter.runtime;

import com.tinet.flowfoundry.interpreter.model.ExecutionNode;
import com.tinet.flowfoundry.interpreter.model.ExecutionPlan;
import com.tinet.flowfoundry.interpreter.model.FlowFoundryTrace;
import com.tinet.flowfoundry.interpreter.model.InterpreterStatus;
import com.tinet.flowfoundry.interpreter.model.NodeKind;
import com.tinet.flowfoundry.run.FlowRunEventCommand;
import com.tinet.flowfoundry.run.FlowRunEventType;
import com.tinet.flowfoundry.run.FlowRunJson;
import com.tinet.flowfoundry.run.FlowRunRecorder;
import io.temporal.activity.LocalActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Emits FlowFoundry-scoped run events via platform Local Activity (worker → platform HTTP). */
public final class WorkflowRunEventEmitter {

  private final String workflowId;
  private final String businessKey;
  private final String namespace;
  private final String runSource;
  private final ExecutionPlan plan;
  private final Map<String, Long> nodeStartedAtEpochMs = new HashMap<>();
  private final List<Promise<Void>> pending = new ArrayList<>();
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
    awaitPending();
  }

  public void workflowFailed(String failureMessage, String failureType) {
    emitTerminal(
        FlowRunEventType.WORKFLOW_FAILED,
        InterpreterStatus.FAILED.name(),
        failureMessage,
        failureType);
    awaitPending();
  }

  /** Records node start time; a single {@link FlowRunEventType#NODE_FINISHED} is emitted on finish. */
  public void beginNode(ExecutionNode node) {
    if (node == null || node.id() == null) {
      return;
    }
    nodeStartedAtEpochMs.put(node.id(), Workflow.currentTimeMillis());
  }

  /** Instant pass-through nodes (e.g. Start) emit one merged lifecycle event. */
  public void nodePassedThrough(ExecutionNode node) {
    if (node == null) {
      return;
    }
    long now = Workflow.currentTimeMillis();
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("kind", nodeKind(node));
    detail.put("startedAtEpochMs", now);
    detail.put("completedAtEpochMs", now);
    emit(FlowRunEventType.NODE_FINISHED, node, "COMPLETED", detail);
  }

  public void finishNode(ExecutionNode node, String status, Map<String, Object> detail) {
    if (node == null) {
      return;
    }
    Map<String, Object> merged = new LinkedHashMap<>();
    if (detail != null) {
      merged.putAll(detail);
    }
    Long started = nodeStartedAtEpochMs.remove(node.id());
    long completed = Workflow.currentTimeMillis();
    if (started != null) {
      merged.putIfAbsent("startedAtEpochMs", started);
    }
    merged.putIfAbsent("completedAtEpochMs", completed);
    if (!merged.containsKey("kind")) {
      merged.put("kind", nodeKind(node));
    }
    emit(FlowRunEventType.NODE_FINISHED, node, status, merged);
  }

  public void nodeFailed(ExecutionNode node, String message) {
    finishNode(
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

  public void timerFired(ExecutionNode node, long durationMs) {
    finishNode(
        node,
        "COMPLETED",
        Map.of("durationMs", durationMs));
  }

  public void humanTaskCompleted(ExecutionNode node, String outcome) {
    finishNode(
        node,
        "COMPLETED",
        outcome == null ? Map.of() : Map.of("outcome", outcome));
  }

  public void childWorkflowCompleted(ExecutionNode node, String childWorkflowId, Object resultSummary) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("childWorkflowId", childWorkflowId);
    String childName = childWorkflowDisplayName(node);
    if (childName != null) {
      detail.put("childWorkflowName", childName);
    }
    if (resultSummary != null) {
      detail.put("result", resultSummary);
    }
    finishNode(node, "COMPLETED", detail);
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
    pending.add(
        Async.procedure(
            () -> {
              try {
                recorder().recordEvent(command);
              } catch (Exception e) {
                Workflow.getLogger(WorkflowRunEventEmitter.class)
                    .warn("Failed to record flow run event {}", command.eventType(), e);
              }
            }));
  }

  private void awaitPending() {
    if (pending.isEmpty()) {
      return;
    }
    Promise.allOf(pending.toArray(Promise[]::new)).get();
    pending.clear();
  }

  private static FlowRunRecorder recorder() {
    return Workflow.newLocalActivityStub(
        FlowRunRecorder.class,
        LocalActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(5))
            .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
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
