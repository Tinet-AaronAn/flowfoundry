package com.tinet.flowfoundry.interpreter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinet.flowfoundry.activity.ActivityTypes;
import com.tinet.flowfoundry.interpreter.model.ExecutionNode;
import com.tinet.flowfoundry.interpreter.model.ExecutionPlan;
import com.tinet.flowfoundry.interpreter.model.FlowFoundryTrace;
import com.tinet.flowfoundry.interpreter.model.HumanTaskCompletion;
import com.tinet.flowfoundry.interpreter.model.HumanTaskNodeState;
import com.tinet.flowfoundry.interpreter.model.InterpreterState;
import com.tinet.flowfoundry.interpreter.model.InterpreterStatus;
import com.tinet.flowfoundry.interpreter.model.NodeKind;
import com.tinet.flowfoundry.interpreter.runtime.ActivityExecutionContext;
import com.tinet.flowfoundry.interpreter.runtime.MappingEvaluator;
import com.tinet.flowfoundry.interpreter.runtime.RunSource;
import com.tinet.flowfoundry.interpreter.runtime.TimerEvaluator;
import com.tinet.flowfoundry.interpreter.runtime.VariableStore;
import com.tinet.flowfoundry.interpreter.runtime.WorkflowRunEventEmitter;
import com.tinet.flowfoundry.workflow.WorkflowRunId;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.ActivityStub;
import io.temporal.workflow.Async;
import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.Promise;
import io.temporal.workflow.TimerOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FlowInterpreterWorkflowImpl implements FlowInterpreterWorkflow {

  private static final Duration DEFAULT_ACTIVITY_TIMEOUT = Duration.ofMinutes(1);
  private static final String ROUTER_ACTIVITY_TYPE = "dynamic-activity-router";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final FlowInterpreterEngine engine = new FlowInterpreterEngine();
  private final MappingEvaluator mappings = new MappingEvaluator();

  private ExecutionPlan plan;
  private String businessKey;
  private RunSource runSource = RunSource.PRODUCTION;
  private String workflowId;
  private VariableStore variables = new VariableStore(Map.of());
  private InterpreterStatus status = InterpreterStatus.RUNNING;
  private String currentNodeId;
  private String currentNodeName;
  private String currentActivityType;
  private String waitingHumanTaskNodeId;
  private HumanTaskCompletion pendingHumanTaskCompletion;
  private final Deque<String> pendingSignals = new ArrayDeque<>();
  private WorkflowRunEventEmitter runEvents;

  @Override
  public InterpreterState run(
      ExecutionPlan plan, String businessKey, Map<String, Object> input, String runSource) {
    this.plan = plan;
    this.businessKey = businessKey;
    this.runSource = RunSource.fromWire(runSource);
    this.variables = new VariableStore(input);
    this.status = InterpreterStatus.RUNNING;
    this.currentNodeId = plan.startNodeId();
    ensureWorkflowId();
    this.runEvents =
        new WorkflowRunEventEmitter(
            workflowId, businessKey, this.runSource.wireValue(), plan);
    runEvents.workflowStarted();

    try {
      engine.runUntilEnd(plan, variables, temporalPort());
      status = InterpreterStatus.COMPLETED;
      currentNodeId = null;
      runEvents.workflowCompleted();
      return getState();
    } catch (RuntimeException e) {
      status = InterpreterStatus.FAILED;
      runEvents.workflowFailed(e.getMessage(), e.getClass().getSimpleName());
      throw e;
    } catch (Exception e) {
      status = InterpreterStatus.FAILED;
      runEvents.workflowFailed(e.getMessage(), e.getClass().getSimpleName());
      throw new RuntimeException(e);
    }
  }

  @Override
  public InterpreterState getState() {
    return new InterpreterState(
        plan == null ? null : plan.flowId(),
        plan == null ? null : plan.version(),
        businessKey,
        runSource == null ? RunSource.PRODUCTION.wireValue() : runSource.wireValue(),
        status,
        currentNodeId,
        currentNodeName,
        currentActivityType,
        waitingHumanTaskNodeId,
        variables.variables(),
        variables.lastResult());
  }

  @Override
  public List<HumanTaskNodeState> getHumanTasks() {
    if (plan == null || plan.nodes() == null) {
      return List.of();
    }
    List<HumanTaskNodeState> tasks = new ArrayList<>();
    for (ExecutionNode node : plan.nodes().values()) {
      if (!isHumanTaskNode(node)) {
        continue;
      }
      String mode = humanTaskMode(node);
      tasks.add(new HumanTaskNodeState(node.id(), mode, node.id().equals(waitingHumanTaskNodeId)));
    }
    return tasks;
  }

  @Override
  public void completeHumanTask(HumanTaskCompletion completion) {
    if (completion == null || completion.nodeId() == null) {
      return;
    }
    this.pendingHumanTaskCompletion = completion;
  }

  @Override
  public void receiveFlowSignal(String signalName, Map<String, Object> payload) {
    if (signalName == null || signalName.isBlank()) {
      return;
    }
    pendingSignals.addLast(signalName.trim());
  }

  private FlowInterpreterEngine.EnginePort temporalPort() {
    return new FlowInterpreterEngine.EnginePort() {
      @Override
      public void onEnterNode(ExecutionNode node) {
        currentNodeId = node.id();
        FlowFoundryTrace trace = FlowFoundryTrace.fromNode(node);
        currentNodeName = trace.nodeName();
        currentActivityType = trace.activityType();
        if (currentActivityType == null && node.activityType() != null) {
          currentActivityType = node.activityType();
        }
        Workflow.setCurrentDetails(trace.workflowDetailsLine());
        if (runEvents != null) {
          if (node.requiredKind() == NodeKind.START) {
            runEvents.nodePassedThrough(node);
          } else {
            runEvents.beginNode(node);
          }
        }
      }

      @Override
      public void onNodeFinished(ExecutionNode node, Map<String, Object> detail) {
        if (runEvents == null || node == null) {
          return;
        }
        if (node.requiredKind() == NodeKind.GATEWAY || node.requiredKind() == NodeKind.START) {
          return;
        }
        runEvents.finishNode(node, "COMPLETED", detail);
      }

      @Override
      public void onGatewayRouted(ExecutionNode node, List<String> targetNodeIds) {
        if (runEvents != null && node != null && node.requiredKind() == NodeKind.GATEWAY) {
          runEvents.gatewayRouted(node, targetNodeIds);
        }
      }

      @Override
      public Object executeActivity(
          ExecutionNode node, VariableStore branchVariables, Map<String, Object> input) {
        ActivityStub activity =
            Workflow.newUntypedActivityStub(
                ActivityOptions.newBuilder()
                    .setTaskQueue(node.taskQueue())
                    .setStartToCloseTimeout(parseDuration(node.timeout(), DEFAULT_ACTIVITY_TIMEOUT))
                    .setRetryOptions(
                        RetryOptions.newBuilder()
                            .setMaximumAttempts(
                                node.maxAttempts() == null ? 3 : node.maxAttempts())
                            .build())
                    .setSummary(FlowFoundryTrace.fromNode(node).activitySummary())
                    .build());
        Object result =
            activity.execute(
                ROUTER_ACTIVITY_TYPE, Object.class, node.activityType(), input);
        branchVariables.setLastResult(result);
        return result;
      }

      @Override
      public void executeTimer(ExecutionNode node, long durationMs) throws Exception {
        if (durationMs <= 0 || runSource.usesStubActivities()) {
          return;
        }
        Workflow.newTimer(
                Duration.ofMillis(durationMs),
                TimerOptions.newBuilder()
                    .setSummary(
                        FlowFoundryTrace.fromNode(node).timerSummary(durationMs + "ms"))
                    .build())
            .get();
        if (runEvents != null) {
          runEvents.timerFired(node, durationMs);
        }
      }

      @Override
      public Object executeChildWorkflow(
          ExecutionNode node, VariableStore branchVariables, Map<String, Object> childInput) {
        ExecutionPlan childPlan = childExecutionPlan(node);
        String childBusinessKey = businessKey + "/" + node.id();
        String childWorkflowId =
            WorkflowRunId.forChildWorkflow(runSource, childPlan.flowId(), childBusinessKey);
        FlowInterpreterWorkflow child =
            Workflow.newChildWorkflowStub(
                FlowInterpreterWorkflow.class,
                ChildWorkflowOptions.newBuilder()
                    .setWorkflowId(childWorkflowId)
                    .setTaskQueue(childTaskQueue(node))
                    .build());
        Object result = child.run(childPlan, childBusinessKey, childInput, runSource.wireValue());
        if (runEvents != null) {
          runEvents.childWorkflowCompleted(node, childWorkflowId, summarizeResult(result));
        }
        return result;
      }

      @Override
      public void awaitHumanTaskIfNeeded(ExecutionNode node, VariableStore branchVariables) {
        if (!isHumanTaskNode(node)) {
          return;
        }
        waitingHumanTaskNodeId = node.id();
        status = InterpreterStatus.WAITING_HUMAN_TASK;
        Workflow.await(
            () ->
                pendingHumanTaskCompletion != null
                    && node.id().equals(pendingHumanTaskCompletion.nodeId()));
        status = InterpreterStatus.RUNNING;
        String outcome =
            pendingHumanTaskCompletion == null ? null : pendingHumanTaskCompletion.outcome();
        waitingHumanTaskNodeId = null;
        mappings.applyOutput(
            branchVariables, pendingHumanTaskCompletion.variables(), node.outputMapping());
        branchVariables.assign(
            "humanTask." + node.id() + ".outcome", outcome);
        if (runEvents != null) {
          runEvents.humanTaskCompleted(node, outcome);
        }
        pendingHumanTaskCompletion = null;
      }

      @Override
      public boolean awaitSignal(String signalName) {
        Workflow.await(
            () -> pendingSignals.stream().anyMatch(name -> name.equals(signalName)));
        pendingSignals.removeIf(name -> name.equals(signalName));
        return true;
      }

      @Override
      public void runBranches(FlowInterpreterEngine.EnginePort.BranchRunner runner, int branchCount)
          throws Exception {
        if (branchCount <= 1) {
          if (branchCount == 1) {
            runner.runBranch(0);
          }
          return;
        }
        List<Promise<Void>> promises = new ArrayList<>();
        for (int i = 0; i < branchCount; i++) {
          int branchIndex = i;
          promises.add(
              Async.procedure(
                  () -> {
                    try {
                      runner.runBranch(branchIndex);
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                  }));
        }
        Promise.allOf(promises).get();
      }

      @Override
      public int raceEventBranches(List<ExecutionNode> eventNodes, VariableStore branchVariables)
          throws Exception {
        if (eventNodes.size() == 1) {
          return 0;
        }
        List<Promise<Integer>> promises = new ArrayList<>();
        for (int i = 0; i < eventNodes.size(); i++) {
          int idx = i;
          promises.add(
              Async.function(
                  () -> {
                    waitForEventNode(eventNodes.get(idx), branchVariables);
                    return idx;
                  }));
        }
        Promise.anyOf(promises.toArray(Promise[]::new)).get();
        for (int i = 0; i < promises.size(); i++) {
          if (promises.get(i).isCompleted()) {
            return promises.get(i).get();
          }
        }
        return 0;
      }

      @Override
      public void enrichRouterInput(
          ExecutionNode node, VariableStore branchVariables, Map<String, Object> input) {
        ensureWorkflowId();
        input.put(
            ActivityExecutionContext.CONTEXT_KEY,
            new ActivityExecutionContext(runSource, businessKey, workflowId).toMap());
        if (node.config() != null && !node.config().isEmpty()) {
          input.put("_config", node.config());
        }
        if (node.inputArgs() != null && !node.inputArgs().isEmpty()) {
          input.put(
              "_args", List.of(mappings.buildArguments(branchVariables, node.inputArgs())));
        }
        input.put(FlowFoundryTrace.INPUT_KEY, FlowFoundryTrace.fromNode(node).toMap());
      }
    };
  }

  private void waitForEventNode(ExecutionNode node, VariableStore branchVariables) {
    String subtype =
        node.config() == null || node.config().get("eventSubtype") == null
            ? "timer"
            : String.valueOf(node.config().get("eventSubtype"));
    if ("signal".equalsIgnoreCase(subtype) || "message".equalsIgnoreCase(subtype)) {
      Object raw = node.config().get("signalName");
      String signalName = raw == null ? node.id() : String.valueOf(raw);
      Workflow.await(() -> pendingSignals.stream().anyMatch(name -> name.equals(signalName)));
      pendingSignals.removeIf(name -> name.equals(signalName));
      return;
    }
    long durationMs =
        TimerEvaluator.evaluate(node, branchVariables, Workflow.currentTimeMillis()).delayMs();
    if (durationMs > 0 && !runSource.usesStubActivities()) {
      Workflow.newTimer(Duration.ofMillis(durationMs)).get();
    }
  }

  private boolean isHumanTaskNode(ExecutionNode node) {
    if (node.requiredKind() == NodeKind.HUMAN_TASK) {
      return true;
    }
    return node.requiredKind() == NodeKind.ACTIVITY
        && ActivityTypes.isHumanTaskActivity(node.activityType());
  }

  private String humanTaskMode(ExecutionNode node) {
    return ActivityTypes.humanTaskMode(node.config() == null ? Map.of() : node.config());
  }

  private ExecutionPlan childExecutionPlan(ExecutionNode node) {
    Object raw = node.config() == null ? null : node.config().get("childExecutionPlan");
    if (raw == null) {
      throw new IllegalArgumentException(
          "Workflow node requires compiled childExecutionPlan: " + node.id());
    }
    return OBJECT_MAPPER.convertValue(raw, ExecutionPlan.class);
  }

  private String childTaskQueue(ExecutionNode node) {
    Object taskQueue = node.config() == null ? null : node.config().get("childTaskQueue");
    if (taskQueue == null || String.valueOf(taskQueue).isBlank()) {
      taskQueue = node.taskQueue();
    }
    return String.valueOf(taskQueue);
  }

  private void ensureWorkflowId() {
    if (workflowId == null || workflowId.isBlank()) {
      workflowId = Workflow.getInfo().getWorkflowId();
    }
  }

  private static Object summarizeResult(Object result) {
    if (result == null) {
      return null;
    }
    String text = String.valueOf(result);
    return FlowFoundryTrace.truncateSummary(text);
  }

  private static Duration parseDuration(String raw, Duration fallback) {
    if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
      return fallback;
    }
    String value = raw.trim().toLowerCase();
    if (value.endsWith("ms")) {
      return Duration.ofMillis(Long.parseLong(value.replace("ms", "")));
    }
    if (value.endsWith("s")) {
      return Duration.ofSeconds(Long.parseLong(value.replace("s", "")));
    }
    if (value.endsWith("m")) {
      return Duration.ofMinutes(Long.parseLong(value.replace("m", "")));
    }
    if (value.endsWith("h")) {
      return Duration.ofHours(Long.parseLong(value.replace("h", "")));
    }
    return Duration.parse(raw);
  }
}
