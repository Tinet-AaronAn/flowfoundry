package com.example.platform.flow;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.interpreter.model.NodeKind;
import com.example.platform.registry.ActivityRegistry;
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
                node("Child_Start", "startEvent", Map.of()),
                node("Child_End", "endEvent", Map.of())),
            List.of(new FlowEdge("Child_Start", "Child_End", "default")));
    FlowDefinition parent =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("ParentFlow", "Parent Flow", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "startEvent", Map.of()),
                node(
                    "CallChild",
                    "workflow",
                    Map.of(
                        "childWorkflowId", "ChildFlow",
                        "childWorkflowVersion", "1.0.0",
                        "childWorkflowDefinition", child)),
                node("End", "endEvent", Map.of())),
            List.of(
                new FlowEdge("Start", "CallChild", "default"),
                new FlowEdge("CallChild", "End", "default")));

    var plan = compiler.compile(parent);
    var childNode = plan.requireNode("CallChild");

    assertThat(childNode.kind()).isEqualTo(NodeKind.CHILD_WORKFLOW);
    assertThat(childNode.config()).containsKey("childExecutionPlan");
  }

  private FlowNode node(String id, String kind, Map<String, Object> config) {
    return new FlowNode(id, kind, null, null, null, null, null, null, null, null, null, config);
  }
}
