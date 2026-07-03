package com.example.platform.interpreter;

import com.example.platform.interpreter.model.ExecutionEdge;
import com.example.platform.interpreter.model.ExecutionNode;
import com.example.platform.interpreter.model.ExecutionPlan;
import com.example.platform.interpreter.model.HumanTaskCompletion;
import com.example.platform.interpreter.model.InterpreterState;
import com.example.platform.interpreter.model.InterpreterStatus;
import com.example.platform.interpreter.runtime.ConditionEvaluator;
import com.example.platform.interpreter.runtime.MappingEvaluator;
import com.example.platform.interpreter.runtime.VariableStore;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.ActivityStub;
import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.Workflow;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
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
  private VariableStore variables = new VariableStore(Map.of());
  private InterpreterStatus status = InterpreterStatus.RUNNING;
  private String currentNodeId;
  private String waitingHumanTaskNodeId;
  private HumanTaskCompletion pendingHumanTaskCompletion;

  @Override
  public InterpreterState run(
      ExecutionPlan plan, String businessKey, Map<String, Object> input) {
    this.plan = plan;
    this.businessKey = businessKey;
    this.variables = new VariableStore(input);
    this.status = InterpreterStatus.RUNNING;
    this.currentNodeId = plan.startNodeId();

    try {
      while (currentNodeId != null) {
        ExecutionNode node = plan.requireNode(currentNodeId);
        executeNode(node);
        if (node.requiredKind() == com.example.platform.interpreter.model.NodeKind.END) {
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
        status,
        currentNodeId,
        waitingHumanTaskNodeId,
        variables.variables(),
        variables.lastResult());
  }

  @Override
  public void completeHumanTask(HumanTaskCompletion completion) {
    if (completion == null || completion.nodeId() == null) {
      return;
    }
    this.pendingHumanTaskCompletion = completion;
  }

  private void executeNode(ExecutionNode node) {
    switch (node.requiredKind()) {
      case START, END, DECISION -> {
        // Routing is handled by outgoing edge conditions.
      }
      case ACTIVITY -> executeActivity(node);
      case TIMER -> executeTimer(node);
      case HUMAN_TASK -> executeHumanTask(node);
      case SCRIPT_TASK -> executeScriptTask(node);
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
                .build());

    Object result =
        activity.execute(
            ROUTER_ACTIVITY_TYPE, Object.class, node.activityType(), routerInput(node));
    mappings.applyOutput(variables, result, node.outputMapping());
  }

  private void executeChildWorkflow(ExecutionNode node) {
    ExecutionPlan childPlan = childExecutionPlan(node);
    String childBusinessKey = businessKey + "/" + node.id();
    Map<String, Object> childInput = mappings.buildInput(variables, node.inputMapping());
    if (childInput.isEmpty()) {
      childInput = variables.variables();
    }
    FlowInterpreterWorkflow child =
        Workflow.newChildWorkflowStub(
            FlowInterpreterWorkflow.class,
            ChildWorkflowOptions.newBuilder()
                .setWorkflowId("flow-child-" + childPlan.flowId() + "-" + childBusinessKey)
                .setTaskQueue(childTaskQueue(node))
                .build());
    InterpreterState result = child.run(childPlan, childBusinessKey, childInput);
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
    Map<String, Object> input = new LinkedHashMap<>();
    input.putAll(mappings.buildInput(variables, node.inputMapping()));
    if (node.config() != null && !node.config().isEmpty()) {
      input.put("_config", node.config());
    }
    if (node.inputArgs() != null && !node.inputArgs().isEmpty()) {
      input.put("_args", List.of(mappings.buildArguments(variables, node.inputArgs())));
    }
    return input;
  }

  private void executeTimer(ExecutionNode node) {
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
    if (!parsed.isZero()) {
      Workflow.sleep(parsed);
    }
  }

  private void executeHumanTask(ExecutionNode node) {
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

  private String humanTaskMode(ExecutionNode node) {
    if (node.config() == null) {
      return "managed";
    }
    Object raw = node.config().get("flowFoundryHumanTask");
    if (raw instanceof Map<?, ?> map) {
      Object mode = map.get("mode");
      if (mode != null && !String.valueOf(mode).isBlank()) {
        return String.valueOf(mode);
      }
    }
    return "managed";
  }

  private void executeScriptTask(ExecutionNode node) {
    if (node.config() == null) {
      return;
    }
    Object rawScript = node.config().get("script");
    Object rawFormat = node.config().getOrDefault("scriptFormat", "feel");
    if (rawScript == null || !"feel".equalsIgnoreCase(String.valueOf(rawFormat))) {
      return;
    }
    String script = String.valueOf(rawScript).trim();
    int assignment = script.indexOf(":=");
    if (assignment <= 0) {
      throw new IllegalArgumentException("Only FEEL assignment scripts are supported: " + node.id());
    }
    String variableName = script.substring(0, assignment).trim();
    String expression = script.substring(assignment + 2).trim();
    variables.assign(variableName, evaluateSimpleFeelExpression(expression));
  }

  private Object evaluateSimpleFeelExpression(String expression) {
    String trimmed = expression.trim();
    int plus = trimmed.indexOf('+');
    if (plus > 0) {
      Object left = resolveScriptValue(trimmed.substring(0, plus));
      Object right = resolveScriptValue(trimmed.substring(plus + 1));
      if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
        if (leftNumber instanceof Double || rightNumber instanceof Double) {
          return leftNumber.doubleValue() + rightNumber.doubleValue();
        }
        return leftNumber.longValue() + rightNumber.longValue();
      }
      return String.valueOf(left) + right;
    }
    return resolveScriptValue(trimmed);
  }

  private Object resolveScriptValue(String raw) {
    String value = raw.trim();
    if (value.matches("-?\\d+")) {
      return Long.parseLong(value);
    }
    if (value.matches("-?\\d+\\.\\d+")) {
      return Double.parseDouble(value);
    }
    if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
      return Boolean.parseBoolean(value);
    }
    if ((value.startsWith("\"") && value.endsWith("\""))
        || (value.startsWith("'") && value.endsWith("'"))) {
      return value.substring(1, value.length() - 1);
    }
    return variables.resolve(value);
  }

  private String selectNextNode(ExecutionNode node) {
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
                .build());
    Map<String, Object> input = new LinkedHashMap<>();
    input.putAll(variables.snapshot());
    input.put("_config", config);
    Object result = activity.execute(ROUTER_ACTIVITY_TYPE, Object.class, "dmn-decision", input);
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
