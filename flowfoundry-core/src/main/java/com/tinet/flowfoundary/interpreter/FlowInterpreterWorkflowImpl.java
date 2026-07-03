package com.tinet.flowfoundary.interpreter;

import com.tinet.flowfoundary.interpreter.model.ExecutionEdge;
import com.tinet.flowfoundary.interpreter.model.ExecutionNode;
import com.tinet.flowfoundary.interpreter.model.ExecutionPlan;
import com.tinet.flowfoundary.interpreter.model.FlowFoundryTrace;
import com.tinet.flowfoundary.interpreter.model.HumanTaskCompletion;
import com.tinet.flowfoundary.interpreter.model.HumanTaskNodeState;
import com.tinet.flowfoundary.interpreter.model.InterpreterState;
import com.tinet.flowfoundary.interpreter.model.InterpreterStatus;
import com.tinet.flowfoundary.interpreter.model.NodeKind;
import com.tinet.flowfoundary.interpreter.runtime.ConditionEvaluator;
import com.tinet.flowfoundary.interpreter.runtime.InputMappingMode;
import com.tinet.flowfoundary.interpreter.runtime.MappingEvaluator;
import com.tinet.flowfoundary.interpreter.runtime.ActivityExecutionContext;
import com.tinet.flowfoundary.interpreter.runtime.RunSource;
import com.tinet.flowfoundary.interpreter.runtime.VariableStore;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.ActivityStub;
import io.temporal.workflow.ChildWorkflowOptions;
import com.tinet.flowfoundary.activity.HumanTaskActivity;
import com.tinet.flowfoundary.activity.ActivityTypes;
import com.tinet.flowfoundary.workflow.WorkflowRunId;
import io.temporal.workflow.TimerOptions;
import io.temporal.workflow.Workflow;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FlowInterpreterWorkflowImpl implements FlowInterpreterWorkflow {

  private static final Duration DEFAULT_ACTIVITY_TIMEOUT = Duration.ofMinutes(1);
  private static final String ROUTER_ACTIVITY_TYPE = "dynamic-activity-router";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final ConditionEvaluator conditions = new ConditionEvaluator();
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

  @Override
  public InterpreterState run(
      ExecutionPlan plan, String businessKey, Map<String, Object> input, String runSource) {
    this.plan = plan;
    this.businessKey = businessKey;
    this.runSource = RunSource.fromWire(runSource);
    this.variables = new VariableStore(input);
    this.status = InterpreterStatus.RUNNING;
    this.currentNodeId = plan.startNodeId();

    try {
      while (currentNodeId != null) {
        ExecutionNode node = plan.requireNode(currentNodeId);
        executeNode(node);
        if (node.requiredKind() == com.tinet.flowfoundary.interpreter.model.NodeKind.END) {
          status = InterpreterStatus.COMPLETED;
          currentNodeId = null;
        } else {
          currentNodeId = selectNextNode(node);
        }
      }
      status = InterpreterStatus.COMPLETED;
      return getState();
    } catch (RuntimeException e) {
      status = InterpreterStatus.FAILED;
      throw e;
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

  private void executeNode(ExecutionNode node) {
    markCurrentNode(node);
    switch (node.requiredKind()) {
      case START, END, GATEWAY -> {
        // Routing is handled by outgoing edge conditions.
      }
      case ACTIVITY -> executeActivity(node);
      case INTERMEDIATE_EVENT -> executeIntermediateEvent(node);
      case HUMAN_TASK -> executeActivity(normalizeHumanTaskNode(node));
      case CHILD_WORKFLOW -> executeChildWorkflow(node);
    }
  }

  private void executeActivity(ExecutionNode node) {
    if (node.activityType() == null || node.activityType().isBlank()) {
      throw new IllegalArgumentException("Activity node requires activityType: " + node.id());
    }

    ActivityStub activity =
        Workflow.newUntypedActivityStub(
            ActivityOptions.newBuilder()
                .setTaskQueue(node.taskQueue())
                .setStartToCloseTimeout(parseDuration(node.timeout(), DEFAULT_ACTIVITY_TIMEOUT))
                .setRetryOptions(
                    RetryOptions.newBuilder()
                        .setMaximumAttempts(node.maxAttempts() == null ? 3 : node.maxAttempts())
                        .build())
                .setSummary(FlowFoundryTrace.fromNode(node).activitySummary())
                .build());

    Object result =
        activity.execute(
            ROUTER_ACTIVITY_TYPE, Object.class, node.activityType(), routerInput(node));
    mappings.applyOutput(variables, result, node.outputMapping());

    if (isHumanTaskNode(node)) {
      awaitHumanTaskCompletionIfNeeded(node);
    }
  }

  private ExecutionNode normalizeHumanTaskNode(ExecutionNode node) {
    if (ActivityTypes.isHumanTaskActivity(node.activityType())) {
      return node;
    }
    Map<String, Object> config =
        node.config() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(node.config());
    config.putIfAbsent("nodeId", node.id());
    return new ExecutionNode(
        node.id(),
        NodeKind.ACTIVITY,
        ActivityTypes.HUMAN_TASK,
        node.taskQueue(),
        node.timeout(),
        node.maxAttempts(),
        node.inputArgs(),
        node.inputMapping(),
        node.outputMapping(),
        config);
  }

  private boolean isHumanTaskNode(ExecutionNode node) {
    if (node.requiredKind() == NodeKind.HUMAN_TASK) {
      return true;
    }
    return node.requiredKind() == NodeKind.ACTIVITY
        && ActivityTypes.isHumanTaskActivity(node.activityType());
  }

  private void awaitHumanTaskCompletionIfNeeded(ExecutionNode node) {
    if ("offline".equalsIgnoreCase(humanTaskMode(node))) {
      variables.assign("humanTask." + node.id() + ".outcome", "offline");
      return;
    }
    waitingHumanTaskNodeId = node.id();
    status = InterpreterStatus.WAITING_HUMAN_TASK;
    Workflow.await(
        () ->
            pendingHumanTaskCompletion != null
                && node.id().equals(pendingHumanTaskCompletion.nodeId()));
    status = InterpreterStatus.RUNNING;
    waitingHumanTaskNodeId = null;
    mappings.applyOutput(variables, pendingHumanTaskCompletion.variables(), node.outputMapping());
    variables.assign("humanTask." + node.id() + ".outcome", pendingHumanTaskCompletion.outcome());
    pendingHumanTaskCompletion = null;
  }

  private void executeChildWorkflow(ExecutionNode node) {
    ExecutionPlan childPlan = childExecutionPlan(node);
    String childBusinessKey = businessKey + "/" + node.id();
    Map<String, Object> childInput =
        mappings.buildInput(variables, node.inputMapping(), inputMappingMode(node));
    if (childInput.isEmpty()) {
      childInput = variables.variables();
    }
    FlowInterpreterWorkflow child =
        Workflow.newChildWorkflowStub(
            FlowInterpreterWorkflow.class,
            ChildWorkflowOptions.newBuilder()
                .setWorkflowId(
                    WorkflowRunId.forChildWorkflow(
                        runSource, childPlan.flowId(), childBusinessKey))
                .setTaskQueue(childTaskQueue(node))
                .build());
    InterpreterState result = child.run(childPlan, childBusinessKey, childInput, runSource.wireValue());
    mappings.applyOutput(variables, result, node.outputMapping());
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

  private Map<String, Object> routerInput(ExecutionNode node) {
    ensureWorkflowId();
    Map<String, Object> input = new LinkedHashMap<>();
    input.putAll(mappings.buildInput(variables, node.inputMapping(), inputMappingMode(node)));
    input.put(
        ActivityExecutionContext.CONTEXT_KEY,
        new ActivityExecutionContext(runSource, businessKey, workflowId).toMap());
    if (node.config() != null && !node.config().isEmpty()) {
      input.put("_config", node.config());
    }
    if (node.inputArgs() != null && !node.inputArgs().isEmpty()) {
      input.put("_args", List.of(mappings.buildArguments(variables, node.inputArgs())));
    }
    input.put(FlowFoundryTrace.INPUT_KEY, FlowFoundryTrace.fromNode(node).toMap());
    return input;
  }

  private InputMappingMode inputMappingMode(ExecutionNode node) {
    if (node.config() == null) {
      return InputMappingMode.PASSTHROUGH_UNMAPPED;
    }
    Object raw = node.config().get("inputMappingMode");
    if (raw == null) {
      return InputMappingMode.PASSTHROUGH_UNMAPPED;
    }
    return InputMappingMode.fromWire(String.valueOf(raw));
  }

  private void markCurrentNode(ExecutionNode node) {
    FlowFoundryTrace trace = FlowFoundryTrace.fromNode(node);
    currentNodeName = trace.nodeName();
    currentActivityType = trace.activityType();
    if (currentActivityType == null && node.activityType() != null) {
      currentActivityType = node.activityType();
    }
    Workflow.setCurrentDetails(trace.workflowDetailsLine());
  }

  private void executeIntermediateEvent(ExecutionNode node) {
    String subtype = intermediateEventSubtype(node);
    switch (subtype) {
      case "timer", "duration" -> executeTimerWait(node);
      case "message", "signal", "boundary" -> throw new UnsupportedOperationException(
          "Intermediate event subtype '" + subtype + "' is not yet supported: " + node.id());
      default -> executeTimerWait(node);
    }
  }

  private String intermediateEventSubtype(ExecutionNode node) {
    if (node.config() == null) {
      return "timer";
    }
    Object subtype = node.config().get("eventSubtype");
    if (subtype != null && !String.valueOf(subtype).isBlank()) {
      return String.valueOf(subtype);
    }
    if (node.config().get("duration") != null || node.config().get("timerDefinition") != null) {
      return "timer";
    }
    return "timer";
  }

  private void executeTimerWait(ExecutionNode node) {
    Object duration = node.config() == null ? null : node.config().get("duration");
    if (duration == null) {
      duration = node.config() == null ? null : node.config().get("durationExpression");
    }
    if (duration == null && node.config() != null) {
      Object timerDefinition = node.config().get("timerDefinition");
      if (timerDefinition instanceof Map<?, ?> definition) {
        duration = definition.get("value");
      }
    }
    Duration parsed = parseDuration(String.valueOf(duration), Duration.ZERO);
    if (parsed.isNegative()) {
      throw new IllegalArgumentException("Timer duration cannot be negative: " + node.id());
    }
    if (!parsed.isZero() && !runSource.usesStubActivities()) {
      String durationLabel = duration == null ? null : String.valueOf(duration);
      Workflow.newTimer(
              parsed,
              TimerOptions.newBuilder()
                  .setSummary(FlowFoundryTrace.fromNode(node).timerSummary(durationLabel))
                  .build())
          .get();
    }
  }

  private String humanTaskMode(ExecutionNode node) {
    return HumanTaskActivity.humanTaskMode(
        node.config() == null ? Map.of() : node.config());
  }

  private String selectNextNode(ExecutionNode node) {
    if (node.requiredKind() == NodeKind.GATEWAY) {
      return selectGatewayNextNode(node);
    }
    return selectExclusiveNextNode(node);
  }

  private String selectGatewayNextNode(ExecutionNode node) {
    String gatewayKind = gatewayKind(node);
    return switch (gatewayKind) {
      case "exclusive" -> selectExclusiveNextNode(node);
      case "parallel", "inclusive", "eventBased" -> throw new UnsupportedOperationException(
          "Gateway kind '" + gatewayKind + "' is not yet supported by the interpreter: " + node.id());
      default -> selectExclusiveNextNode(node);
    };
  }

  private String gatewayKind(ExecutionNode node) {
    if (node.config() == null) {
      return "exclusive";
    }
    Object raw = node.config().get("gatewayKind");
    if (raw == null || String.valueOf(raw).isBlank()) {
      return "exclusive";
    }
    return String.valueOf(raw);
  }

  private String selectExclusiveNextNode(ExecutionNode node) {
    List<ExecutionEdge> outgoing = plan.outgoingEdges(node.id());
    ExecutionEdge defaultEdge = null;
    for (ExecutionEdge edge : outgoing) {
      if (edge.isDefault()) {
        defaultEdge = edge;
      } else if (isDmnCondition(edge.condition())) {
        if (evaluateDmnCondition(node, edge.condition())) {
          return edge.target();
        }
      } else if (conditions.evaluate(edge.condition(), variables)) {
        return edge.target();
      }
    }
    return defaultEdge == null ? null : defaultEdge.target();
  }

  private boolean isDmnCondition(Object condition) {
    if (!(condition instanceof Map<?, ?> map)) {
      return false;
    }
    Object type = map.get("type");
    if (type == null) {
      type = map.get("language");
    }
    return type != null && "dmn".equalsIgnoreCase(String.valueOf(type));
  }

  private boolean evaluateDmnCondition(ExecutionNode node, Object condition) {
    @SuppressWarnings("unchecked")
    Map<String, Object> config = (Map<String, Object>) condition;
    ActivityStub activity =
        Workflow.newUntypedActivityStub(
            ActivityOptions.newBuilder()
                .setTaskQueue(node.taskQueue())
                .setStartToCloseTimeout(DEFAULT_ACTIVITY_TIMEOUT)
                .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
                .setSummary(
                    FlowFoundryTrace.fromNode(node).activitySummary() + " [dmn-edge]")
                .build());
    ensureWorkflowId();
    Map<String, Object> input = new LinkedHashMap<>();
    input.putAll(variables.snapshot());
    input.put(
        ActivityExecutionContext.CONTEXT_KEY,
        new ActivityExecutionContext(runSource, businessKey, workflowId).toMap());
    input.put("_config", config);
    input.put(FlowFoundryTrace.INPUT_KEY, FlowFoundryTrace.fromNode(node).toMap());
    Object result = activity.execute(ROUTER_ACTIVITY_TYPE, Object.class, ActivityTypes.SCRIPT_RUNTIME, input);
    if (result instanceof Boolean bool) {
      return bool;
    }
    if (result instanceof Map<?, ?> map) {
      Object matched = map.get("matched");
      if (matched instanceof Boolean bool) {
        return bool;
      }
      Object output = map.get("output");
      if (output instanceof Boolean bool) {
        return bool;
      }
    }
    return result != null;
  }

  private void ensureWorkflowId() {
    if (workflowId == null || workflowId.isBlank()) {
      workflowId = Workflow.getInfo().getWorkflowId();
    }
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
