package com.tinet.flowfoundary.interpreter;

import com.tinet.flowfoundary.interpreter.model.ExecutionNode;
import com.tinet.flowfoundary.interpreter.model.ExecutionPlan;
import com.tinet.flowfoundary.interpreter.runtime.VariableStore;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** In-memory {@link FlowInterpreterEngine.EnginePort} for unit tests. */
final class SyncEnginePort implements FlowInterpreterEngine.EnginePort {

  private final Map<String, Object> activityResults = new HashMap<>();
  private final Deque<String> signals = new ArrayDeque<>();
  private final List<String> visitedNodes = new ArrayList<>();
  private final AtomicInteger activityCalls = new AtomicInteger();
  private boolean autoCompleteManagedHumanTasks;

  static SyncEnginePort withActivityResult(String activityType, Object result) {
    SyncEnginePort port = new SyncEnginePort();
    port.registerActivity(activityType, result);
    return port;
  }

  void registerActivity(String activityType, Object result) {
    activityResults.put(activityType, result);
  }

  void signal(String signalName) {
    signals.addLast(signalName);
  }

  List<String> visitedNodes() {
    return List.copyOf(visitedNodes);
  }

  int activityCalls() {
    return activityCalls.get();
  }

  @Override
  public void onEnterNode(ExecutionNode node) {
    visitedNodes.add(node.id());
  }

  @Override
  public Object executeActivity(
      ExecutionNode node, VariableStore branchVariables, Map<String, Object> input) {
    activityCalls.incrementAndGet();
    Object result = activityResults.getOrDefault(node.activityType(), Map.of("ok", true));
    branchVariables.setLastResult(result);
    return result;
  }

  @Override
  public void executeTimer(ExecutionNode node, long durationMs) {}

  @Override
  public Object executeChildWorkflow(
      ExecutionNode node, VariableStore branchVariables, Map<String, Object> childInput) {
    return Map.of();
  }

  void setAutoCompleteManagedHumanTasks(boolean autoCompleteManagedHumanTasks) {
    this.autoCompleteManagedHumanTasks = autoCompleteManagedHumanTasks;
  }

  @Override
  public void awaitHumanTaskIfNeeded(ExecutionNode node, VariableStore branchVariables) {
    if (autoCompleteManagedHumanTasks) {
      branchVariables.assign("humanTask." + node.id() + ".outcome", "approved");
    }
  }

  @Override
  public boolean awaitSignal(String signalName) {
    if (signals.isEmpty()) {
      return false;
    }
    String next = signals.removeFirst();
    return next.equals(signalName);
  }

  @Override
  public void runBranches(FlowInterpreterEngine.EnginePort.BranchRunner runner, int branchCount)
      throws Exception {
    for (int i = 0; i < branchCount; i++) {
      runner.runBranch(i);
    }
  }

  @Override
  public int raceEventBranches(List<ExecutionNode> eventNodes, VariableStore branchVariables) {
    long bestMs = Long.MAX_VALUE;
    int bestIdx = 0;
    for (int i = 0; i < eventNodes.size(); i++) {
      ExecutionNode node = eventNodes.get(i);
      String subtype =
          node.config() == null || node.config().get("eventSubtype") == null
              ? "timer"
              : String.valueOf(node.config().get("eventSubtype"));
      if ("signal".equalsIgnoreCase(subtype)) {
        Object raw = node.config().get("signalName");
        String signalName = raw == null ? node.id() : String.valueOf(raw);
        if (signals.contains(signalName)) {
          return i;
        }
        continue;
      }
      long ms = timerMs(node);
      if (ms < bestMs) {
        bestMs = ms;
        bestIdx = i;
      }
    }
    return bestIdx;
  }

  @Override
  public void enrichRouterInput(
      ExecutionNode node, VariableStore branchVariables, Map<String, Object> input) {}

  private static long timerMs(ExecutionNode node) {
    Object duration = node.config() == null ? null : node.config().get("duration");
    if (duration == null) {
      return Long.MAX_VALUE;
    }
    String raw = String.valueOf(duration).trim().toLowerCase();
    if (raw.endsWith("ms")) {
      return Long.parseLong(raw.replace("ms", ""));
    }
    if (raw.endsWith("s")) {
      return Long.parseLong(raw.replace("s", "")) * 1000L;
    }
    return Long.MAX_VALUE;
  }
}
