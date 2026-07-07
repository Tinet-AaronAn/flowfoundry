package com.tinet.flowfoundry.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tinet.flowfoundry.interpreter.model.ExecutionEdge;
import com.tinet.flowfoundry.interpreter.model.NodeKind;
import com.tinet.flowfoundry.activity.ActivityTypes;
import com.tinet.flowfoundry.registry.ActivityRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlowCompilerTest {

  private final FlowCompiler compiler =
      new FlowCompiler(new ActivityRegistry("1.0", "test", "test-task-queue", List.of()));

  @Test
  void compilesWorkflowNodeAsChildWorkflow() {
    FlowDefinition child =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("ChildFlow", "Child Flow", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Child_Start", "START", Map.of()),
                node("Child_End", "END", Map.of())),
            List.of(new FlowEdge("Child_Start", "Child_End", "default")));
    FlowDefinition parent =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("ParentFlow", "Parent Flow", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START", Map.of()),
                node(
                    "CallChild",
                    "CHILD_WORKFLOW",
                    Map.of(
                        "childWorkflowId", "ChildFlow",
                        "childWorkflowVersion", "1.0.0",
                        "childWorkflowDefinition", child)),
                node("End", "END", Map.of())),
            List.of(
                new FlowEdge("Start", "CallChild", "default"),
                new FlowEdge("CallChild", "End", "default")));

    var plan = compiler.compile(parent);
    var childNode = plan.requireNode("CallChild");

    assertThat(childNode.kind()).isEqualTo(NodeKind.CHILD_WORKFLOW);
    assertThat(childNode.config()).containsKey("childExecutionPlan");
  }

  @Test
  void compilesParallelGatewaySplitJoinTopology() {
    FlowDefinition definition =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("GatewayFlow", "Gateway Flow", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START", Map.of()),
                node("PG_Split", "GATEWAY", Map.of("gatewayKind", "parallel")),
                activityNode("Task_A", ActivityTypes.SCRIPT_RUNTIME),
                activityNode("Task_B", ActivityTypes.SCRIPT_RUNTIME),
                node("PG_Join", "GATEWAY", Map.of("gatewayKind", "parallel")),
                node("End", "END", Map.of())),
            List.of(
                new FlowEdge("Start", "PG_Split", "default"),
                new FlowEdge("PG_Split", "Task_A", "default"),
                new FlowEdge("PG_Split", "Task_B", "default"),
                new FlowEdge("Task_A", "PG_Join", "default"),
                new FlowEdge("Task_B", "PG_Join", "default"),
                new FlowEdge("PG_Join", "End", "default")));

    var plan = compiler.compile(definition);

    assertThat(plan.requireNode("PG_Split").config()).containsEntry("gatewayRole", "split");
    assertThat(plan.requireNode("PG_Join").config()).containsEntry("gatewayRole", "join");
    assertThat(plan.requireNode("PG_Join").config()).containsEntry("expectedBranchCount", 2);
  }

  @Test
  void compilesParallelGatewaySplitWithFeelConditions() {
    FlowDefinition definition =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("ParallelFeelFlow", "Parallel Feel Flow", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START", Map.of()),
                node("PG_Split", "GATEWAY", Map.of("gatewayKind", "parallel")),
                activityNode("Task_A", ActivityTypes.SCRIPT_RUNTIME),
                activityNode("Task_B", ActivityTypes.SCRIPT_RUNTIME),
                node("PG_Join", "GATEWAY", Map.of("gatewayKind", "parallel")),
                node("End", "END", Map.of())),
            List.of(
                new FlowEdge("Start", "PG_Split", "default"),
                new FlowEdge("PG_Split", "Task_A", "${branchA == true}", 0),
                new FlowEdge("PG_Split", "Task_B", "${branchB == true}", 1),
                new FlowEdge("Task_A", "PG_Join", "default"),
                new FlowEdge("Task_B", "PG_Join", "default"),
                new FlowEdge("PG_Join", "End", "default")));

    var plan = compiler.compile(definition);

    assertThat(plan.requireNode("PG_Split").config()).containsEntry("gatewayRole", "split");
    assertThat(plan.outgoingEdges("PG_Split")).hasSize(2);
  }

  @Test
  void rejectsInvalidParallelGatewayTopology() {
    FlowDefinition definition =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("BadGatewayFlow", "Bad Gateway Flow", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START", Map.of()),
                node("Gateway", "GATEWAY", Map.of("gatewayKind", "parallel")),
                node("End", "END", Map.of())),
            List.of(
                new FlowEdge("Start", "Gateway", "default"),
                new FlowEdge("Gateway", "End", "default")));

    assertThatThrownBy(() -> compiler.compile(definition))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void compilesIntermediateEventWithEventSubtype() {
    FlowDefinition definition =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("EventFlow", "Event Flow", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START", Map.of()),
                node(
                    "Wait",
                    "INTERMEDIATE_EVENT",
                    Map.of("eventSubtype", "timer", "duration", "1m")),
                node("End", "END", Map.of())),
            List.of(
                new FlowEdge("Start", "Wait", "default"),
                new FlowEdge("Wait", "End", "default")));

    var plan = compiler.compile(definition);

    assertThat(plan.requireNode("Wait").kind()).isEqualTo(NodeKind.INTERMEDIATE_EVENT);
    assertThat(plan.requireNode("Wait").config()).containsEntry("eventSubtype", "timer");
  }

  @Test
  void compilesGatewayNodesAsGatewayKind() {
    FlowDefinition definition =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("GatewayFlow", "Gateway Flow", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START", Map.of()),
                node("Gateway", "GATEWAY", Map.of()),
                node("End", "END", Map.of())),
            List.of(
                new FlowEdge("Start", "Gateway", "default"),
                new FlowEdge("Gateway", "End", "default")));

    var plan = compiler.compile(definition);

    assertThat(plan.requireNode("Gateway").kind()).isEqualTo(NodeKind.GATEWAY);
  }

  @Test
  void compilesActivityTraceMetadata() {
    FlowDefinition definition =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("TraceFlow", "Trace Flow", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START", Map.of()),
                new FlowNode(
                    "Task_ImportNumbers",
                    "ACTIVITY",
                    "Import number batch",
                    "serviceTask",
                    "script-runtime",
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
                    Map.of()),
                node("End", "END", Map.of())),
            List.of(
                new FlowEdge("Start", "Task_ImportNumbers", "default"),
                new FlowEdge("Task_ImportNumbers", "End", "default")));

    var plan = compiler.compile(definition);
    @SuppressWarnings("unchecked")
    Map<String, Object> trace =
        (Map<String, Object>) plan.requireNode("Task_ImportNumbers").config().get("flowFoundryTrace");

    assertThat(trace)
        .containsEntry("nodeId", "Task_ImportNumbers")
        .containsEntry("nodeName", "Import number batch")
        .containsEntry("canvasKind", "serviceTask")
        .containsEntry("activityType", "script-runtime");
  }

  @Test
  void compilesHumanTaskAsCoreActivity() {
    FlowDefinition definition =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("HumanFlow", "Human Flow", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START", Map.of()),
                new FlowNode(
                    "Review",
                    "ACTIVITY",
                    "Owner Review",
                    "humanTask",
                    ActivityTypes.HUMAN_TASK,
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
                    Map.of(
                        "flowFoundryHumanTask", Map.of("mode", "managed"),
                        "flowFoundryAssignmentDefinition", Map.of("candidateGroups", "ops"))),
                node("End", "END", Map.of())),
            List.of(
                new FlowEdge("Start", "Review", "default"),
                new FlowEdge("Review", "End", "default")));

    var plan = compiler.compile(definition);
    var compiled = plan.requireNode("Review");

    assertThat(compiled.kind()).isEqualTo(NodeKind.ACTIVITY);
    assertThat(compiled.activityType()).isEqualTo(ActivityTypes.HUMAN_TASK);
    assertThat(compiled.config()).containsEntry("nodeId", "Review");
  }

  @Test
  void compilesLegacyHumanTaskKindAsCoreActivity() {
    FlowDefinition definition =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("LegacyHumanFlow", "Legacy Human Flow", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START", Map.of()),
                node("Review", "HUMAN_TASK", Map.of("flowFoundryHumanTask", Map.of("mode", "offline"))),
                node("End", "END", Map.of())),
            List.of(
                new FlowEdge("Start", "Review", "default"),
                new FlowEdge("Review", "End", "default")));

    var plan = compiler.compile(definition);

    assertThat(plan.requireNode("Review").kind()).isEqualTo(NodeKind.ACTIVITY);
    assertThat(plan.requireNode("Review").activityType()).isEqualTo(ActivityTypes.HUMAN_TASK);
  }

  @Test
  void compilesScriptTaskAsScriptRuntimeActivity() {
    FlowDefinition definition =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("ScriptFlow", "Script Flow", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START", Map.of()),
                new FlowNode(
                    "Script",
                    "ACTIVITY",
                    "Decide next action",
                    "scriptTask",
                    "script-runtime",
                    null,
                    null,
                    null,
                    "demo-script",
                    "1.0.0",
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of()),
                node("End", "END", Map.of())),
            List.of(
                new FlowEdge("Start", "Script", "default"),
                new FlowEdge("Script", "End", "default")));

    var plan = compiler.compile(definition);

    assertThat(plan.requireNode("Script").kind()).isEqualTo(NodeKind.ACTIVITY);
    assertThat(plan.requireNode("Script").activityType()).isEqualTo("script-runtime");
    assertThat(plan.requireNode("Script").config()).containsEntry("scriptCodeId", "demo-script");
  }

  @Test
  void rejectsGenericTaskAtCompileTime() {
    FlowDefinition definition =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("SketchFlow", "Sketch Flow", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START", Map.of()),
                node("Sketch", "task", Map.of()),
                node("End", "END", Map.of())),
            List.of(
                new FlowEdge("Start", "Sketch", "default"),
                new FlowEdge("Sketch", "End", "default")));

    assertThatThrownBy(() -> compiler.compile(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Generic Task");
  }

  @Test
  void rejectsActivityWithMultipleOutgoingEdges() {
    FlowDefinition definition =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("MultiOutFlow", "Multi Out Flow", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START", Map.of()),
                activityNode("Task", ActivityTypes.SCRIPT_RUNTIME),
                node("EndA", "END", Map.of()),
                node("EndB", "END", Map.of())),
            List.of(
                new FlowEdge("Start", "Task", "default"),
                new FlowEdge("Task", "EndA", "default"),
                new FlowEdge("Task", "EndB", "default")));

    assertThatThrownBy(() -> compiler.compile(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at most one outgoing edge");
  }

  @Test
  void rejectsStartWithMultipleOutgoingEdges() {
    FlowDefinition definition =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("StartMultiOutFlow", "Start Multi Out Flow", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START", Map.of()),
                activityNode("TaskA", ActivityTypes.SCRIPT_RUNTIME),
                activityNode("TaskB", ActivityTypes.SCRIPT_RUNTIME),
                node("End", "END", Map.of())),
            List.of(
                new FlowEdge("Start", "TaskA", "default"),
                new FlowEdge("Start", "TaskB", "default"),
                new FlowEdge("TaskA", "End", "default"),
                new FlowEdge("TaskB", "End", "default")));

    assertThatThrownBy(() -> compiler.compile(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Start node allows at most one outgoing edge");
  }

  @Test
  void rejectsActivityOutgoingEdgeWithCondition() {
    FlowDefinition definition =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("CondOutFlow", "Conditional Out Flow", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START", Map.of()),
                activityNode("Task", ActivityTypes.SCRIPT_RUNTIME),
                node("End", "END", Map.of())),
            List.of(
                new FlowEdge("Start", "Task", "default"),
                new FlowEdge("Task", "End", "${approved == true}")));

    assertThatThrownBy(() -> compiler.compile(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not have a condition");
  }

  @Test
  void ordersGatewayOutgoingEdgesByPriority() {
    FlowDefinition definition =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("PriorityFlow", "Priority Flow", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START", Map.of()),
                node("Gateway", "GATEWAY", Map.of("gatewayKind", "exclusive")),
                node("BranchA", "END", Map.of()),
                node("BranchB", "END", Map.of()),
                node("BranchC", "END", Map.of())),
            List.of(
                new FlowEdge("Start", "Gateway", "default"),
                new FlowEdge("Gateway", "BranchA", "${a == 1}", 2),
                new FlowEdge("Gateway", "BranchB", "${b == 1}", 0),
                new FlowEdge("Gateway", "BranchC", "default", 1)));

    var plan = compiler.compile(definition);

    assertThat(plan.outgoingEdges("Gateway"))
        .extracting(ExecutionEdge::target)
        .containsExactly("BranchB", "BranchC", "BranchA");
  }

  @Test
  void compilesActivityWithStandardLoop() {
    FlowDefinition definition =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("LoopFlow", "Loop Flow", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START", Map.of()),
                activityNode(
                    "Task",
                    ActivityTypes.SCRIPT_RUNTIME,
                    Map.of(
                        "flowFoundryLoop",
                        Map.of(
                            "mode", "standard",
                            "condition", "${remaining > 0}",
                            "maxIterations", 50))),
                node("End", "END", Map.of())),
            List.of(
                new FlowEdge("Start", "Task", "default"),
                new FlowEdge("Task", "End", "default")));

    var plan = compiler.compile(definition);

    @SuppressWarnings("unchecked")
    Map<String, Object> loop =
        (Map<String, Object>) plan.requireNode("Task").config().get("flowFoundryLoop");
    assertThat(loop)
        .containsEntry("mode", "standard")
        .containsEntry("condition", "${remaining > 0}")
        .containsEntry("maxIterations", 50);
  }

  @Test
  void rejectsLoopOnGateway() {
    FlowDefinition definition =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("BadLoopFlow", "Bad Loop Flow", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START", Map.of()),
                node(
                    "Gateway",
                    "GATEWAY",
                    Map.of(
                        "gatewayKind", "exclusive",
                        "flowFoundryLoop", Map.of("mode", "standard", "condition", "true"))),
                node("End", "END", Map.of())),
            List.of(
                new FlowEdge("Start", "Gateway", "default"),
                new FlowEdge("Gateway", "End", "default")));

    assertThatThrownBy(() -> compiler.compile(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("only supported on ACTIVITY");
  }

  @Test
  void rejectsStandardLoopWithoutCondition() {
    FlowDefinition definition =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("NoCondLoop", "No Cond Loop", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START", Map.of()),
                activityNode(
                    "Task",
                    ActivityTypes.SCRIPT_RUNTIME,
                    Map.of("flowFoundryLoop", Map.of("mode", "standard"))),
                node("End", "END", Map.of())),
            List.of(
                new FlowEdge("Start", "Task", "default"),
                new FlowEdge("Task", "End", "default")));

    assertThatThrownBy(() -> compiler.compile(definition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("flowFoundryLoop.condition");
  }

  @Test
  void preservesTaskHeadersInActivityConfig() {
    FlowDefinition definition =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("HeaderFlow", "Header Flow", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START", Map.of()),
                activityNode(
                    "Task_A",
                    ActivityTypes.SCRIPT_RUNTIME,
                    Map.of(
                        "scriptCodeId",
                        "demo",
                        "taskHeaders",
                        Map.of("x-tenant", "demo", "x-priority", "high"))),
                node("End", "END", Map.of())),
            List.of(
                new FlowEdge("Start", "Task_A", "default"),
                new FlowEdge("Task_A", "End", "default")));

    var plan = compiler.compile(definition);
    var taskNode = plan.requireNode("Task_A");

    assertThat(taskNode.config()).containsKey("taskHeaders");
    assertThat(taskNode.config().get("taskHeaders"))
        .isEqualTo(Map.of("x-tenant", "demo", "x-priority", "high"));
  }

  private FlowNode activityNode(String id, String activityType) {
    return activityNode(id, activityType, Map.of());
  }

  private FlowNode activityNode(String id, String activityType, Map<String, Object> config) {
    return new FlowNode(
        id,
        "ACTIVITY",
        id,
        "serviceTask",
        activityType,
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
        config);
  }

  private FlowNode node(String id, String kind, Map<String, Object> config) {
    return new FlowNode(
        id, kind, null, null, null, null, null, null, null, null, null, null, null, null, null, config);
  }
}
