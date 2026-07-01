package com.example.platform.flow;

import com.example.platform.interpreter.model.ExecutionEdge;
import com.example.platform.interpreter.model.ExecutionNode;
import com.example.platform.interpreter.model.ExecutionPlan;
import com.example.platform.interpreter.model.NodeKind;
import com.example.platform.registry.ActivityRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class FlowCompiler {

  private final ActivityRegistry activityRegistry;
  private final SafeFeelCompiler safeFeelCompiler = new SafeFeelCompiler();

  public FlowCompiler(ActivityRegistry activityRegistry) {
    this.activityRegistry = activityRegistry;
  }

  public ExecutionPlan compile(FlowDefinition definition) {
    if (definition == null) {
      throw new IllegalArgumentException("Flow definition is required");
    }
    if (definition.flow() == null || blank(definition.flow().id())) {
      throw new IllegalArgumentException("flow.id is required");
    }
    if (definition.nodes() == null || definition.nodes().isEmpty()) {
      throw new IllegalArgumentException("At least one node is required");
    }

    Map<String, ExecutionNode> nodes = new LinkedHashMap<>();
    String startNodeId = null;
    int endCount = 0;

    for (FlowNode node : definition.nodes()) {
      if (node == null || blank(node.id())) {
        throw new IllegalArgumentException("Every node requires id");
      }
      if (nodes.containsKey(node.id())) {
        throw new IllegalArgumentException("Duplicate node id: " + node.id());
      }
      NodeKind kind = normalizeKind(node.kind());
      if (kind == NodeKind.START) {
        if (startNodeId != null) {
          throw new IllegalArgumentException("Only one start node is allowed");
        }
        startNodeId = node.id();
      }
      if (kind == NodeKind.END) {
        endCount++;
      }
      ExecutionNode executionNode = compileNode(node, kind);
      nodes.put(node.id(), executionNode);
    }

    if (startNodeId == null) {
      throw new IllegalArgumentException("A start node is required");
    }
    if (endCount == 0) {
      throw new IllegalArgumentException("At least one end node is required");
    }

    Map<String, List<ExecutionEdge>> edges = compileEdges(definition.edges(), nodes);
    return new ExecutionPlan(
        definition.flow().id(),
        blank(definition.flow().version()) ? "1.0.0" : definition.flow().version(),
        startNodeId,
        nodes,
        edges);
  }

  private ExecutionNode compileNode(FlowNode node, NodeKind kind) {
    String activityType = node.activityType();
    Map<String, Object> config = node.config() == null ? Map.of() : new LinkedHashMap<>(node.config());
    if (kind == NodeKind.ACTIVITY) {
      if (blank(activityType) && "businessRuleTask".equals(node.kind())) {
        activityType = "dmn-decision";
      }
      if (blank(activityType)) {
        throw new IllegalArgumentException("Activity node requires activityType: " + node.id());
      }
      if (!"dmn-decision".equals(activityType)) {
        activityRegistry.require(activityType);
      }
      if (!blank(node.decisionRef())) {
        config = new LinkedHashMap<>(config);
        config.put("decisionRef", node.decisionRef());
        config.put("decisionVersion", node.decisionVersion());
      }
    }
    if (kind == NodeKind.SCRIPT_TASK) {
      config = new LinkedHashMap<>(config);
      config.putIfAbsent("scriptFormat", config.getOrDefault("scriptFormat", "feel"));
    }
    return new ExecutionNode(
        node.id(),
        kind,
        activityType,
        blank(node.taskQueue()) ? activityRegistry.defaultTaskQueue() : node.taskQueue(),
        node.timeout(),
        node.maxAttempts(),
        node.inputArgs(),
        node.inputMapping(),
        node.outputMapping(),
        config);
  }

  private Map<String, List<ExecutionEdge>> compileEdges(
      List<FlowEdge> flowEdges, Map<String, ExecutionNode> nodes) {
    Map<String, List<ExecutionEdge>> edges = new LinkedHashMap<>();
    if (flowEdges == null) {
      return edges;
    }
    for (FlowEdge edge : flowEdges) {
      if (edge == null || blank(edge.from()) || blank(edge.to())) {
        throw new IllegalArgumentException("Every edge requires from and to");
      }
      if (!nodes.containsKey(edge.from())) {
        throw new IllegalArgumentException("Edge source does not exist: " + edge.from());
      }
      if (!nodes.containsKey(edge.to())) {
        throw new IllegalArgumentException("Edge target does not exist: " + edge.to());
      }
      edges.computeIfAbsent(edge.from(), ignored -> new ArrayList<>())
          .add(new ExecutionEdge(edge.to(), safeFeelCompiler.compile(edge.condition())));
    }
    return edges;
  }

  private static NodeKind normalizeKind(String raw) {
    String value = Objects.requireNonNullElse(raw, "").trim();
    return switch (value) {
      case "startEvent", "start" -> NodeKind.START;
      case "endEvent", "end" -> NodeKind.END;
      case "task", "genericTask", "serviceTask", "sendTask", "businessRuleTask", "callActivity",
              "activity" ->
          NodeKind.ACTIVITY;
      case "userTask", "manualTask", "humanTask" -> NodeKind.HUMAN_TASK;
      case "receiveTask", "exclusiveGateway", "inclusiveGateway", "parallelGateway",
              "eventBasedGateway", "decision" ->
          NodeKind.DECISION;
      case "timerEvent", "intermediateCatchEvent", "boundaryEvent", "timer" -> NodeKind.TIMER;
      case "scriptTask" -> NodeKind.SCRIPT_TASK;
      default -> NodeKind.from(value);
    };
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
