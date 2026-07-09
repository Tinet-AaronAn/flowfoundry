package com.tinet.flowfoundry.workflow.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tinet.flowfoundry.flow.FlowDefinition;
import com.tinet.flowfoundry.flow.FlowNode;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CanvasToDslConverterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final CanvasToDslConverter converter = new CanvasToDslConverter(objectMapper);

  @Test
  void convertsSimpleLinearFlow() {
    ObjectNode model = objectMapper.createObjectNode();
    model.put("id", "workflow_abc12345");
    model.put("name", "Demo");
    ArrayNode nodes = model.putArray("nodes");
    nodes.add(node("start_1", "startEvent", "Start"));
    nodes.add(serviceTask("task_1", "Do Work", "demo.doWork"));
    nodes.add(node("end_1", "endEvent", "End"));
    nodes.add(node("note_1", "textAnnotation", "Note"));
    ArrayNode edges = model.putArray("edges");
    edges.add(edge("start_1", "task_1"));
    edges.add(edge("task_1", "end_1"));

    FlowDefinition dsl =
        converter.convert(model, "1.0.0", Set.of("workflow_abc12345"), (a, b, c) -> null);

    assertThat(dsl.dslVersion()).isEqualTo("1.0");
    assertThat(dsl.flow().id()).isEqualTo("workflow_abc12345");
    assertThat(dsl.flow().version()).isEqualTo("1.0.0");
    assertThat(dsl.nodes()).hasSize(3);
    assertThat(dsl.nodes().stream().map(FlowNode::kind)).containsExactly("START", "ACTIVITY", "END");
    assertThat(dsl.edges()).hasSize(2);
    FlowNode activity =
        dsl.nodes().stream().filter(n -> "ACTIVITY".equals(n.kind())).findFirst().orElseThrow();
    assertThat(activity.activityType()).isEqualTo("demo.doWork");
    assertThat(activity.canvasKind()).isEqualTo("serviceTask");
  }

  @Test
  void embedsChildWorkflowDefinition() {
    ObjectNode parent = objectMapper.createObjectNode();
    parent.put("id", "workflow_parent01");
    parent.put("name", "Parent");
    ArrayNode nodes = parent.putArray("nodes");
    nodes.add(node("start_1", "startEvent", "Start"));
    ObjectNode childNode = node("child_1", "workflow", "Call Child");
    ObjectNode childConfig = childNode.putObject("config");
    childConfig.put("childWorkflowId", "workflow_child001");
    childConfig.put("childWorkflowVersion", "1.0.0");
    nodes.add(childNode);
    nodes.add(node("end_1", "endEvent", "End"));
    ArrayNode edges = parent.putArray("edges");
    edges.add(edge("start_1", "child_1"));
    edges.add(edge("child_1", "end_1"));

    FlowDefinition childDsl =
        new FlowDefinition(
            "1.0",
            new com.tinet.flowfoundry.flow.FlowMetadata("workflow_child001", "Child", "1.0.0"),
            Map.of(),
            Map.of(),
            java.util.List.of(),
            java.util.List.of());

    FlowDefinition dsl =
        converter.convert(
            parent,
            "1.0.0",
            new HashSet<>(Set.of("workflow_parent01")),
            (id, version, seen) -> {
              assertThat(id).isEqualTo("workflow_child001");
              assertThat(version).isEqualTo("1.0.0");
              assertThat(seen).contains("workflow_parent01", "workflow_child001");
              return childDsl;
            });

    FlowNode child =
        dsl.nodes().stream()
            .filter(n -> "CHILD_WORKFLOW".equals(n.kind()))
            .findFirst()
            .orElseThrow();
    assertThat(child.config().get("childWorkflowDefinition")).isEqualTo(childDsl);
    assertThat(child.config().get("flowFoundryChildWorkflow")).isInstanceOf(Map.class);
  }

  @Test
  void rejectsEmptyRuntimeGraph() {
    ObjectNode model = objectMapper.createObjectNode();
    model.put("id", "workflow_empty001");
    model.put("name", "Empty");
    model.putArray("nodes").add(node("p1", "participant", "Lane"));
    model.putArray("edges");

    assertThatThrownBy(
            () -> converter.convert(model, "1.0.0", Set.of("workflow_empty001"), (a, b, c) -> null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no compilable runtime nodes");
  }

  private ObjectNode node(String id, String kind, String name) {
    ObjectNode n = objectMapper.createObjectNode();
    n.put("id", id);
    n.put("kind", kind);
    n.put("name", name);
    return n;
  }

  private ObjectNode serviceTask(String id, String name, String activityType) {
    ObjectNode n = node(id, "serviceTask", name);
    n.put("activityType", activityType);
    return n;
  }

  private ObjectNode edge(String from, String to) {
    ObjectNode e = objectMapper.createObjectNode();
    e.put("from", from);
    e.put("to", to);
    e.put("condition", "default");
    return e;
  }
}
