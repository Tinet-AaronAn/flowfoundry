package com.tinet.flowfoundary.interpreter;

import com.tinet.flowfoundary.flow.LoopDefinition;
import com.tinet.flowfoundary.interpreter.model.ExecutionEdge;
import com.tinet.flowfoundary.interpreter.model.ExecutionNode;
import com.tinet.flowfoundary.interpreter.model.ExecutionPlan;
import com.tinet.flowfoundary.interpreter.model.NodeKind;
import com.tinet.flowfoundary.interpreter.runtime.ConditionEvaluator;
import com.tinet.flowfoundary.interpreter.runtime.InputMappingMode;
import com.tinet.flowfoundary.interpreter.runtime.MappingEvaluator;
import com.tinet.flowfoundary.interpreter.runtime.VariableStore;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic flow traversal; used by tests (sync) and {@link FlowInterpreterWorkflowImpl}. */
public final class FlowInterpreterEngine {

  private final ConditionEvaluator conditions = new ConditionEvaluator();
  private final MappingEvaluator mappings = new MappingEvaluator();

  public void runUntilEnd(ExecutionPlan plan, VariableStore variables, EnginePort port)
      throws Exception {
    String next = plan.startNodeId();
    while (next != null) {
      next = runFrom(next, null, plan, variables, port);
    }
  }

  /** Runs until {@code stopBeforeNodeId} is reached (exclusive). Returns null when finished. */
  public String runFrom(
      String startNodeId,
      String stopBeforeNodeId,
      ExecutionPlan plan,
      VariableStore variables,
      EnginePort port)
      throws Exception {
    String current = startNodeId;
    while (current != null) {
      if (stopBeforeNodeId != null && stopBeforeNodeId.equals(current)) {
        return null;
      }
      ExecutionNode node = plan.requireNode(current);
      port.onEnterNode(node);
      if (node.requiredKind() == NodeKind.END) {
        return null;
      }
      if (node.requiredKind() == NodeKind.GATEWAY) {
        String gatewayKind = gatewayKind(node);
        String gatewayRole = gatewayRole(node);
        if (("parallel".equals(gatewayKind) || "inclusive".equals(gatewayKind))
            && "split".equals(gatewayRole)) {
          current = handleForkJoinSplit(node, gatewayKind, plan, variables, port);
          continue;
        }
        if ("eventBased".equals(gatewayKind) && "split".equals(gatewayRole)) {
          current = handleEventBasedSplit(node, plan, variables, port);
          continue;
        }
      }
      executeNode(node, plan, variables, port);
      current = selectNextSimple(node, plan, variables);
    }
    return null;
  }

  private String handleForkJoinSplit(
      ExecutionNode split,
      String gatewayKind,
      ExecutionPlan plan,
      VariableStore variables,
      EnginePort port)
      throws Exception {
    String joinId = requiredConfigString(split, "pairedJoinId");
    List<ExecutionEdge> outgoing = plan.outgoingEdges(split.id());
    List<ExecutionEdge> activated =
        "inclusive".equals(gatewayKind)
            ? selectInclusiveOutgoing(outgoing, variables)
            : "parallel".equals(gatewayKind)
                ? selectParallelOutgoing(outgoing, variables)
                : outgoing;
    if (activated.isEmpty()) {
      variables.setLastResult(null);
      List<ExecutionEdge> joinOutgoing = plan.outgoingEdges(joinId);
      return joinOutgoing.isEmpty() ? null : joinOutgoing.get(0).target();
    }
    VariableStore[] branchStores = new VariableStore[activated.size()];
    port.runBranches(
        branchIndex -> {
          ExecutionEdge edge = activated.get(branchIndex);
          VariableStore branchVars = variables.copy();
          runFrom(edge.target(), joinId, plan, branchVars, port);
          branchStores[branchIndex] = branchVars;
        },
        activated.size());
    mergeBranchVariables(variables, Arrays.asList(branchStores));
    variables.setLastResult(null);
    List<ExecutionEdge> joinOutgoing = plan.outgoingEdges(joinId);
    if (joinOutgoing.isEmpty()) {
      return null;
    }
    return joinOutgoing.get(0).target();
  }

  private String handleEventBasedSplit(
      ExecutionNode gateway, ExecutionPlan plan, VariableStore variables, EnginePort port)
      throws Exception {
    List<ExecutionEdge> outgoing = plan.outgoingEdges(gateway.id());
    int winner =
        port.raceEventBranches(
            outgoing.stream()
                .map(edge -> plan.requireNode(edge.target()))
                .toList(),
            variables);
    ExecutionEdge winningEdge = outgoing.get(winner);
    ExecutionNode eventNode = plan.requireNode(winningEdge.target());
    executeIntermediateEvent(eventNode, variables, port);
    List<ExecutionEdge> afterEvent = plan.outgoingEdges(eventNode.id());
    if (afterEvent.isEmpty()) {
      return null;
    }
    return afterEvent.get(0).target();
  }

  private List<ExecutionEdge> selectInclusiveOutgoing(
      List<ExecutionEdge> outgoing, VariableStore variables) {
    List<ExecutionEdge> activated = new ArrayList<>();
    ExecutionEdge defaultEdge = null;
    for (ExecutionEdge edge : outgoing) {
      if (edge.isDefault()) {
        defaultEdge = edge;
      } else if (conditions.evaluate(edge.condition(), variables)) {
        activated.add(edge);
      }
    }
    if (activated.isEmpty() && defaultEdge != null) {
      activated.add(defaultEdge);
    }
    return activated;
  }

  /** Parallel split: fork all branches when unconditional; otherwise fork default + FEEL-true edges. */
  private List<ExecutionEdge> selectParallelOutgoing(
      List<ExecutionEdge> outgoing, VariableStore variables) {
    boolean hasConditional = outgoing.stream().anyMatch(edge -> !edge.isDefault());
    if (!hasConditional) {
      return new ArrayList<>(outgoing);
    }
    List<ExecutionEdge> activated = new ArrayList<>();
    for (ExecutionEdge edge : outgoing) {
      if (edge.isDefault()) {
        activated.add(edge);
      } else if (conditions.evaluate(edge.condition(), variables)) {
        activated.add(edge);
      }
    }
    return activated;
  }

  private void executeNode(
      ExecutionNode node, ExecutionPlan plan, VariableStore variables, EnginePort port)
      throws Exception {
    switch (node.requiredKind()) {
      case START, GATEWAY -> {}
      case ACTIVITY, HUMAN_TASK -> {
        ExecutionNode activityNode =
            node.requiredKind() == NodeKind.HUMAN_TASK ? normalizeHumanTask(node) : node;
        LoopDefinition loop = LoopDefinition.fromConfig(activityNode.config());
        if (loop.isEnabled()) {
          executeActivityWithLoop(activityNode, variables, port, loop);
        } else {
          executeActivityOnce(activityNode, variables, port);
        }
      }
      case INTERMEDIATE_EVENT -> executeIntermediateEvent(node, variables, port);
      case CHILD_WORKFLOW -> {
        Object result = port.executeChildWorkflow(node, variables, childInput(node, variables));
        mappings.applyOutput(variables, result, node.outputMapping());
      }
    }
  }

  private void executeActivityOnce(
      ExecutionNode activityNode, VariableStore variables, EnginePort port) throws Exception {
    Object result =
        port.executeActivity(activityNode, variables, routerInput(activityNode, variables, port));
    mappings.applyOutput(variables, result, activityNode.outputMapping());
    if (isHumanTask(activityNode)) {
      port.awaitHumanTaskIfNeeded(activityNode, variables);
    }
  }

  private void executeActivityWithLoop(
      ExecutionNode activityNode, VariableStore variables, EnginePort port, LoopDefinition loop)
      throws Exception {
    if (loop.isStandard()) {
      executeStandardLoop(activityNode, variables, port, loop);
      return;
    }
    if (loop.isMultiInstance()) {
      executeMultiInstanceLoop(activityNode, variables, port, loop);
      return;
    }
    throw new IllegalStateException("Unsupported loop mode on node: " + activityNode.id());
  }

  private void executeStandardLoop(
      ExecutionNode activityNode, VariableStore variables, EnginePort port, LoopDefinition loop)
      throws Exception {
    int iteration = 0;
    do {
      iteration++;
      variables.assign(loop.iterationVar(), iteration);
      executeActivityOnce(activityNode, variables, port);
    } while (iteration < loop.maxIterations()
        && conditions.evaluate(loop.condition(), variables));
  }

  private void executeMultiInstanceLoop(
      ExecutionNode activityNode, VariableStore variables, EnginePort port, LoopDefinition loop)
      throws Exception {
    List<Object> items = resolveCollection(loop.collection(), variables);
    int limit = Math.min(items.size(), loop.maxIterations());
    for (int index = 0; index < limit; index++) {
      variables.assign(loop.indexVar(), index);
      variables.assign(loop.elementVar(), items.get(index));
      executeActivityOnce(activityNode, variables, port);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<Object> resolveCollection(String expression, VariableStore variables) {
    Object raw = variables.resolve(expression);
    if (raw == null) {
      return List.of();
    }
    if (raw instanceof List<?> list) {
      return new ArrayList<>((List<Object>) list);
    }
    if (raw.getClass().isArray()) {
      List<Object> items = new ArrayList<>();
      int length = Array.getLength(raw);
      for (int i = 0; i < length; i++) {
        items.add(Array.get(raw, i));
      }
      return items;
    }
    throw new IllegalArgumentException(
        "Multi-instance collection must resolve to list or array: " + expression);
  }

  private void executeIntermediateEvent(
      ExecutionNode node, VariableStore variables, EnginePort port) throws Exception {
    String subtype = intermediateEventSubtype(node);
    switch (subtype) {
      case "timer", "duration" -> port.executeTimer(node, timerDurationMs(node));
      case "signal", "message" -> {
        String signalName = signalName(node);
        if (!port.awaitSignal(signalName)) {
          throw new IllegalStateException("Signal not received: " + signalName);
        }
      }
      default -> port.executeTimer(node, timerDurationMs(node));
    }
  }

  private String selectNextSimple(ExecutionNode node, ExecutionPlan plan, VariableStore variables) {
    if (node.requiredKind() == NodeKind.GATEWAY) {
      return selectGatewayNext(node, plan, variables);
    }
    List<ExecutionEdge> outgoing = plan.outgoingEdges(node.id());
    if (outgoing.size() == 1) {
      return outgoing.get(0).target();
    }
    return selectExclusiveNext(node, plan, variables);
  }

  private String selectGatewayNext(
      ExecutionNode node, ExecutionPlan plan, VariableStore variables) {
    String gatewayKind = gatewayKind(node);
    if ("exclusive".equals(gatewayKind) || "pass".equals(gatewayRole(node))) {
      return selectExclusiveNext(node, plan, variables);
    }
    return selectExclusiveNext(node, plan, variables);
  }

  private String selectExclusiveNext(
      ExecutionNode node, ExecutionPlan plan, VariableStore variables) {
    List<ExecutionEdge> outgoing = plan.outgoingEdges(node.id());
    ExecutionEdge defaultEdge = null;
    for (ExecutionEdge edge : outgoing) {
      if (edge.isDefault()) {
        defaultEdge = edge;
      } else if (conditions.evaluate(edge.condition(), variables)) {
        return edge.target();
      }
    }
    return defaultEdge == null ? null : defaultEdge.target();
  }

  private static void mergeBranchVariables(VariableStore target, List<VariableStore> branches) {
    for (VariableStore branch : branches) {
      if (branch != null) {
        target.mergeFrom(branch);
      }
    }
  }

  private Map<String, Object> routerInput(
      ExecutionNode node, VariableStore variables, EnginePort port) {
    Map<String, Object> input = new LinkedHashMap<>();
    input.putAll(
        mappings.buildInput(variables, node.inputMapping(), inputMappingMode(node)));
    port.enrichRouterInput(node, variables, input);
    return input;
  }

  private Map<String, Object> childInput(ExecutionNode node, VariableStore variables) {
    Map<String, Object> childInput =
        mappings.buildInput(variables, node.inputMapping(), inputMappingMode(node));
    if (childInput.isEmpty()) {
      childInput = variables.variables();
    }
    return childInput;
  }

  private static InputMappingMode inputMappingMode(ExecutionNode node) {
    if (node.config() == null) {
      return InputMappingMode.PASSTHROUGH_UNMAPPED;
    }
    Object raw = node.config().get("inputMappingMode");
    if (raw == null) {
      return InputMappingMode.PASSTHROUGH_UNMAPPED;
    }
    return InputMappingMode.fromWire(String.valueOf(raw));
  }

  private static ExecutionNode normalizeHumanTask(ExecutionNode node) {
    return node;
  }

  private static boolean isHumanTask(ExecutionNode node) {
    return node.requiredKind() == NodeKind.HUMAN_TASK
        || "human-task".equals(node.activityType());
  }

  private static String gatewayKind(ExecutionNode node) {
    if (node.config() == null) {
      return "exclusive";
    }
    Object raw = node.config().get("gatewayKind");
    return raw == null || String.valueOf(raw).isBlank() ? "exclusive" : String.valueOf(raw);
  }

  private static String gatewayRole(ExecutionNode node) {
    if (node.config() == null) {
      return "pass";
    }
    Object raw = node.config().get("gatewayRole");
    return raw == null ? "pass" : String.valueOf(raw);
  }

  private static String requiredConfigString(ExecutionNode node, String key) {
    if (node.config() == null || node.config().get(key) == null) {
      throw new IllegalStateException("Missing gateway config " + key + " on " + node.id());
    }
    return String.valueOf(node.config().get(key));
  }

  private static String intermediateEventSubtype(ExecutionNode node) {
    if (node.config() == null) {
      return "timer";
    }
    Object subtype = node.config().get("eventSubtype");
    if (subtype != null && !String.valueOf(subtype).isBlank()) {
      return String.valueOf(subtype);
    }
    return "timer";
  }

  private static String signalName(ExecutionNode node) {
    if (node.config() == null) {
      throw new IllegalArgumentException("Signal event requires signalName: " + node.id());
    }
    Object raw = node.config().get("signalName");
    if (raw == null || String.valueOf(raw).isBlank()) {
      throw new IllegalArgumentException("Signal event requires signalName: " + node.id());
    }
    return String.valueOf(raw);
  }

  private static long timerDurationMs(ExecutionNode node) {
    Object duration = node.config() == null ? null : node.config().get("duration");
    if (duration == null && node.config() != null) {
      Object timerDefinition = node.config().get("timerDefinition");
      if (timerDefinition instanceof Map<?, ?> map) {
        duration = map.get("value");
      }
    }
    if (duration == null) {
      return 0L;
    }
    return parseDurationMs(String.valueOf(duration));
  }

  private static long parseDurationMs(String raw) {
    if (raw == null || raw.isBlank()) {
      return 0L;
    }
    String value = raw.trim().toLowerCase();
    if (value.endsWith("ms")) {
      return Long.parseLong(value.replace("ms", ""));
    }
    if (value.endsWith("s")) {
      return Long.parseLong(value.replace("s", "")) * 1000L;
    }
    if (value.endsWith("m")) {
      return Long.parseLong(value.replace("m", "")) * 60_000L;
    }
    if (value.endsWith("h")) {
      return Long.parseLong(value.replace("h", "")) * 3_600_000L;
    }
    return 0L;
  }

  /** Port for activity/timer/signal/parallel execution. */
  public interface EnginePort {
    void onEnterNode(ExecutionNode node);

    Object executeActivity(ExecutionNode node, VariableStore variables, Map<String, Object> input)
        throws Exception;

    void executeTimer(ExecutionNode node, long durationMs) throws Exception;

    Object executeChildWorkflow(
        ExecutionNode node, VariableStore variables, Map<String, Object> childInput)
        throws Exception;

    void awaitHumanTaskIfNeeded(ExecutionNode node, VariableStore variables) throws Exception;

    boolean awaitSignal(String signalName) throws Exception;

    void runBranches(BranchRunner runner, int branchCount) throws Exception;

    /** @return index of winning branch for event-based gateway */
    int raceEventBranches(List<ExecutionNode> eventNodes, VariableStore variables) throws Exception;

    void enrichRouterInput(ExecutionNode node, VariableStore variables, Map<String, Object> input);

    @FunctionalInterface
    interface BranchRunner {
      void runBranch(int branchIndex) throws Exception;
    }
  }
}
