package com.tinet.flowfoundary.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tinet.flowfoundary.activity.ActivityTypes;
import com.tinet.flowfoundary.flow.FlowCompiler;
import com.tinet.flowfoundary.flow.FlowDefinition;
import com.tinet.flowfoundary.flow.FlowEdge;
import com.tinet.flowfoundary.flow.FlowMetadata;
import com.tinet.flowfoundary.flow.FlowNode;
import com.tinet.flowfoundary.interpreter.model.ExecutionPlan;
import com.tinet.flowfoundary.interpreter.runtime.VariableStore;
import com.tinet.flowfoundary.registry.ActivityRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlowInterpreterEngineTest {

  private final FlowCompiler compiler =
      new FlowCompiler(new ActivityRegistry("1.0", "test", "test-task-queue", List.of()));

  @Test
  void runsParallelSplitJoin() throws Exception {
    ExecutionPlan plan =
        compiler.compile(
            new FlowDefinition(
                "1.0",
                new FlowMetadata("ParallelFlow", "Parallel", "1.0.0"),
                Map.of(),
                Map.of(),
                List.of(
                    node("Start", "START"),
                    gateway("PG_Split", "parallel", "split"),
                    activity("Task_A"),
                    activity("Task_B"),
                    gateway("PG_Join", "parallel", "join"),
                    node("End", "END")),
                List.of(
                    edge("Start", "PG_Split"),
                    edge("PG_Split", "Task_A"),
                    edge("PG_Split", "Task_B"),
                    edge("Task_A", "PG_Join"),
                    edge("Task_B", "PG_Join"),
                    edge("PG_Join", "End"))));

    SyncEnginePort port = SyncEnginePort.withActivityResult(ActivityTypes.SCRIPT_RUNTIME, Map.of());

    VariableStore variables = new VariableStore(Map.of());
    new FlowInterpreterEngine().runUntilEnd(plan, variables, port);

    assertThat(port.visitedNodes()).contains("Task_A", "Task_B", "End");
    assertThat(variables.lastResult()).isNull();
    assertThat(port.activityCalls()).isEqualTo(2);
  }

  @Test
  void runsInclusiveSplitJoinOnlyActivatedBranches() throws Exception {
    ExecutionPlan plan =
        compiler.compile(
            new FlowDefinition(
                "1.0",
                new FlowMetadata("InclusiveFlow", "Inclusive", "1.0.0"),
                Map.of(),
                Map.of(),
                List.of(
                    node("Start", "START"),
                    gateway("IG_Split", "inclusive", "split"),
                    activity("Task_A"),
                    activity("Task_B"),
                    gateway("IG_Join", "inclusive", "join"),
                    node("End", "END")),
                List.of(
                    edge("Start", "IG_Split"),
                    new FlowEdge("IG_Split", "Task_A", "${flag == true}", 0),
                    new FlowEdge("IG_Split", "Task_B", "${flag == false}", 1),
                    edge("Task_A", "IG_Join"),
                    edge("Task_B", "IG_Join"),
                    edge("IG_Join", "End"))));

    SyncEnginePort port = SyncEnginePort.withActivityResult(ActivityTypes.SCRIPT_RUNTIME, Map.of());
    VariableStore variables = new VariableStore(Map.of("flag", true));
    new FlowInterpreterEngine().runUntilEnd(plan, variables, port);

    assertThat(port.visitedNodes()).contains("Task_A").doesNotContain("Task_B");
    assertThat(port.activityCalls()).isEqualTo(1);
    assertThat(variables.lastResult()).isNull();
  }

  @Test
  void runsExclusiveGatewayByPriority() throws Exception {
    ExecutionPlan plan =
        compiler.compile(
            new FlowDefinition(
                "1.0",
                new FlowMetadata("ExclusiveFlow", "Exclusive", "1.0.0"),
                Map.of(),
                Map.of(),
                List.of(
                    node("Start", "START"),
                    gateway("EG", "exclusive", null),
                    node("EndA", "END"),
                    node("EndB", "END")),
                List.of(
                    edge("Start", "EG"),
                    new FlowEdge("EG", "EndB", "${pickB == true}", 0),
                    new FlowEdge("EG", "EndA", "${pickA == true}", 1))));

    SyncEnginePort port = new SyncEnginePort();
    VariableStore vars = new VariableStore(Map.of("pickA", true, "pickB", true));
    new FlowInterpreterEngine().runUntilEnd(plan, vars, port);

    assertThat(port.visitedNodes()).contains("EndB").doesNotContain("EndA");
  }

  @Test
  void rejectsOverlappingParallelOutputMappingsAtCompileTime() {
    FlowDefinition definition =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("OverlapFlow", "Overlap", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START"),
                gateway("PG_Split", "parallel", "split"),
                activityWithOutput("Task_A", Map.of("status", "$.lastResult")),
                activityWithOutput("Task_B", Map.of("status", "$.lastResult")),
                gateway("PG_Join", "parallel", "join"),
                node("End", "END")),
            List.of(
                edge("Start", "PG_Split"),
                edge("PG_Split", "Task_A"),
                edge("PG_Split", "Task_B"),
                edge("Task_A", "PG_Join"),
                edge("Task_B", "PG_Join"),
                edge("PG_Join", "End")));

    assertThatThrownBy(() -> compiler.compile(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("overlapping output mapping");
  }

  private static FlowNode node(String id, String kind) {
    return new FlowNode(id, kind, id, null, null, null, null, null, null, null, null, null, null, null, Map.of());
  }

  private static FlowNode gateway(String id, String gatewayKind, String roleHint) {
    Map<String, Object> config = new java.util.LinkedHashMap<>();
    config.put("gatewayKind", gatewayKind);
    if (roleHint != null) {
      config.put("gatewayRole", roleHint);
    }
    return new FlowNode(
        id, "GATEWAY", id, "parallelGateway", null, null, null, null, null, null, null, null, null, null, config);
  }

  private static FlowNode activity(String id) {
    return new FlowNode(
        id,
        "ACTIVITY",
        id,
        "serviceTask",
        ActivityTypes.SCRIPT_RUNTIME,
        "test-task-queue",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        Map.of());
  }

  private static FlowNode activityWithOutput(String id, Map<String, String> outputMapping) {
    return new FlowNode(
        id,
        "ACTIVITY",
        id,
        "serviceTask",
        ActivityTypes.SCRIPT_RUNTIME,
        "test-task-queue",
        null,
        null,
        null,
        null,
        null,
        null,
        outputMapping,
        null,
        Map.of());
  }

  @Test
  void rejectsNestedParallelGateway() {
    FlowDefinition definition =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("NestedParallel", "Nested", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START"),
                gateway("PG_Outer_Split", "parallel", "split"),
                gateway("PG_Inner_Split", "parallel", "split"),
                activity("Task_A1"),
                activity("Task_A2"),
                activity("Task_B"),
                gateway("PG_Inner_Join", "parallel", "join"),
                gateway("PG_Outer_Join", "parallel", "join"),
                node("End", "END")),
            List.of(
                edge("Start", "PG_Outer_Split"),
                edge("PG_Outer_Split", "PG_Inner_Split"),
                edge("PG_Outer_Split", "Task_B"),
                edge("PG_Inner_Split", "Task_A1"),
                edge("PG_Inner_Split", "Task_A2"),
                edge("Task_A1", "PG_Inner_Join"),
                edge("Task_A2", "PG_Inner_Join"),
                edge("Task_B", "PG_Outer_Join"),
                edge("PG_Inner_Join", "PG_Outer_Join"),
                edge("PG_Outer_Join", "End")));

    assertThatThrownBy(() -> compiler.compile(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Nested parallel");
  }

  @Test
  void runsEventBasedGatewayTimerRace() throws Exception {
    ExecutionPlan plan =
        compiler.compile(
            new FlowDefinition(
                "1.0",
                new FlowMetadata("EventFlow", "Event", "1.0.0"),
                Map.of(),
                Map.of(),
                List.of(
                    node("Start", "START"),
                    gateway("EG", "eventBased", "split"),
                    timerEvent("Timer_Fast", "100ms"),
                    timerEvent("Timer_Slow", "500ms"),
                    node("End_Fast", "END"),
                    node("End_Slow", "END")),
                List.of(
                    edge("Start", "EG"),
                    edge("EG", "Timer_Fast"),
                    edge("EG", "Timer_Slow"),
                    edge("Timer_Fast", "End_Fast"),
                    edge("Timer_Slow", "End_Slow"))));

    SyncEnginePort port = new SyncEnginePort();
    VariableStore variables = new VariableStore(Map.of());
    new FlowInterpreterEngine().runUntilEnd(plan, variables, port);

    assertThat(port.visitedNodes()).contains("End_Fast").doesNotContain("End_Slow");
  }

  private static FlowNode timerEvent(String id, String duration) {
    return new FlowNode(
        id,
        "INTERMEDIATE_EVENT",
        id,
        "intermediateEvent",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        Map.of("eventSubtype", "timer", "duration", duration));
  }

  @Test
  void runsStandardLoopUntilConditionFalse() throws Exception {
    ExecutionPlan plan =
        compiler.compile(
            new FlowDefinition(
                "1.0",
                new FlowMetadata("StandardLoop", "Standard Loop", "1.0.0"),
                Map.of(),
                Map.of(),
                List.of(
                    node("Start", "START"),
                    activityWithLoop(
                        "Task",
                        Map.of(
                            "mode", "standard",
                            "condition", "${loop.iteration < 3}",
                            "iterationVar", "loop.iteration",
                            "maxIterations", 100)),
                    node("End", "END")),
                List.of(edge("Start", "Task"), edge("Task", "End"))));

    SyncEnginePort port = SyncEnginePort.withActivityResult(ActivityTypes.SCRIPT_RUNTIME, Map.of());
    VariableStore variables = new VariableStore(Map.of());
    new FlowInterpreterEngine().runUntilEnd(plan, variables, port);

    assertThat(port.activityCalls()).isEqualTo(3);
    assertThat(variables.resolve("loop.iteration")).isEqualTo(3);
  }

  @Test
  void runsMultiInstanceLoopSequentially() throws Exception {
    ExecutionPlan plan =
        compiler.compile(
            new FlowDefinition(
                "1.0",
                new FlowMetadata("MultiLoop", "Multi Loop", "1.0.0"),
                Map.of(),
                Map.of(),
                List.of(
                    node("Start", "START"),
                    activityWithLoop(
                        "Task",
                        Map.of(
                            "mode", "multiInstance",
                            "collection", "$.vars.items",
                            "elementVar", "loop.item",
                            "indexVar", "loop.index",
                            "maxIterations", 100,
                            "sequential", true)),
                    node("End", "END")),
                List.of(edge("Start", "Task"), edge("Task", "End"))));

    SyncEnginePort port = SyncEnginePort.withActivityResult(ActivityTypes.SCRIPT_RUNTIME, Map.of());
    VariableStore variables = new VariableStore(Map.of());
    variables.assign("items", List.of("a", "b", "c"));
    new FlowInterpreterEngine().runUntilEnd(plan, variables, port);

    assertThat(port.activityCalls()).isEqualTo(3);
    assertThat(variables.resolve("loop.item")).isEqualTo("c");
    assertThat(variables.resolve("loop.index")).isEqualTo(2);
  }

  @Test
  void skipsMultiInstanceLoopWhenCollectionEmpty() throws Exception {
    ExecutionPlan plan =
        compiler.compile(
            new FlowDefinition(
                "1.0",
                new FlowMetadata("EmptyMulti", "Empty Multi", "1.0.0"),
                Map.of(),
                Map.of(),
                List.of(
                    node("Start", "START"),
                    activityWithLoop(
                        "Task",
                        Map.of(
                            "mode", "multiInstance",
                            "collection", "$.vars.items",
                            "elementVar", "loop.item",
                            "indexVar", "loop.index",
                            "maxIterations", 100,
                            "sequential", true)),
                    node("End", "END")),
                List.of(edge("Start", "Task"), edge("Task", "End"))));

    SyncEnginePort port = SyncEnginePort.withActivityResult(ActivityTypes.SCRIPT_RUNTIME, Map.of());
    VariableStore variables = new VariableStore(Map.of());
    variables.assign("items", List.of());
    new FlowInterpreterEngine().runUntilEnd(plan, variables, port);

    assertThat(port.activityCalls()).isZero();
    assertThat(port.visitedNodes()).contains("End");
  }

  private static FlowNode activityWithLoop(String id, Map<String, Object> loopConfig) {
    Map<String, Object> config = new java.util.LinkedHashMap<>();
    config.put("flowFoundryLoop", loopConfig);
    return new FlowNode(
        id,
        "ACTIVITY",
        id,
        "serviceTask",
        ActivityTypes.SCRIPT_RUNTIME,
        "test-task-queue",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        config);
  }

  private static FlowEdge edge(String from, String to) {
    return new FlowEdge(from, to, "default");
  }
}
