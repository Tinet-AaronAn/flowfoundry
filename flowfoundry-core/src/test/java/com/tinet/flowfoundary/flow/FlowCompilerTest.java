package com.tinet.flowfoundary.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tinet.flowfoundary.interpreter.model.NodeKind;
import com.tinet.flowfoundary.activity.ActivityTypes;
import com.tinet.flowfoundary.registry.ActivityRegistry;
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
  void compilesGatewayNodesWithGatewayKind() {
    FlowDefinition definition =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("GatewayFlow", "Gateway Flow", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START", Map.of()),
                node("Gateway", "GATEWAY", Map.of("gatewayKind", "parallel")),
                node("End", "END", Map.of())),
            List.of(
                new FlowEdge("Start", "Gateway", "default"),
                new FlowEdge("Gateway", "End", "default")));

    var plan = compiler.compile(definition);

    assertThat(plan.requireNode("Gateway").kind()).isEqualTo(NodeKind.GATEWAY);
    assertThat(plan.requireNode("Gateway").config()).containsEntry("gatewayKind", "parallel");
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
                    ActivityTypes.HUMAN_TASK,
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
                    "script-runtime",
                    null,
                    null,
                    null,
                    "demo-script",
                    "1.0.0",
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

  private FlowNode node(String id, String kind, Map<String, Object> config) {
    return new FlowNode(id, kind, null, null, null, null, null, null, null, null, null, config);
  }
}
