package com.tinet.flowfoundary.flow;

import com.tinet.flowfoundary.interpreter.model.ExecutionEdge;
import com.tinet.flowfoundary.interpreter.model.ExecutionNode;
import com.tinet.flowfoundary.interpreter.model.ExecutionPlan;
import com.tinet.flowfoundary.interpreter.model.FlowFoundryTrace;
import com.tinet.flowfoundary.interpreter.model.NodeKind;
import com.tinet.flowfoundary.activity.ActivityTypes;
import com.tinet.flowfoundary.registry.ActivityRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class FlowCompiler {

  private final ActivityRegistry activityRegistry;
  private final SafeFeelCompiler safeFeelCompiler = new SafeFeelCompiler();
  private final ObjectMapper objectMapper = new ObjectMapper();

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
      rejectGenericTask(node);
      NodeKind kind = NodeKind.from(node.kind());
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
    if (kind == NodeKind.HUMAN_TASK) {
      kind = NodeKind.ACTIVITY;
      if (blank(activityType)) {
        activityType = ActivityTypes.HUMAN_TASK;
      }
      config = ensureHumanTaskConfig(node, config);
    }
    if (kind == NodeKind.ACTIVITY) {
      if (blank(activityType)) {
        throw new IllegalArgumentException("Activity node requires activityType: " + node.id());
      }
      if (!ActivityTypes.isCore(activityType)) {
        activityRegistry.require(activityType);
      }
      if (!blank(node.decisionRef())) {
        config = new LinkedHashMap<>(config);
        config.put("decisionRef", node.decisionRef());
        config.put("decisionVersion", node.decisionVersion());
      }
      if (ActivityTypes.isHumanTaskActivity(activityType)) {
        config = ensureHumanTaskConfig(node, config);
      }
    }
    if (kind == NodeKind.CHILD_WORKFLOW) {
      config = compileChildWorkflowConfig(node, config);
    }
    if (kind == NodeKind.GATEWAY) {
      config = ensureGatewayConfig(node, config);
    }
    if (kind == NodeKind.INTERMEDIATE_EVENT) {
      config = ensureIntermediateEventConfig(node, config);
    }
    config = ensureInputMappingMode(node, config);
    config = ensureTrace(node, kind, activityType, config);
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

  private Map<String, Object> ensureInputMappingMode(FlowNode node, Map<String, Object> config) {
    if (blank(node.inputMappingMode())) {
      return config;
    }
    Map<String, Object> compiled = new LinkedHashMap<>(config);
    compiled.put("inputMappingMode", node.inputMappingMode().trim());
    return compiled;
  }

  private Map<String, Object> compileChildWorkflowConfig(
      FlowNode node, Map<String, Object> config) {
    Object childWorkflowId = config.get("childWorkflowId");
    Object childWorkflowDefinition = config.get("childWorkflowDefinition");
    if ((childWorkflowId == null || String.valueOf(childWorkflowId).isBlank())
        && childWorkflowDefinition == null) {
      throw new IllegalArgumentException(
          "Workflow node requires childWorkflowId or childWorkflowDefinition: " + node.id());
    }
    Map<String, Object> compiled = new LinkedHashMap<>(config);
    if (childWorkflowDefinition != null) {
      FlowDefinition childDefinition =
          objectMapper.convertValue(childWorkflowDefinition, FlowDefinition.class);
      compiled.put("childExecutionPlan", compile(childDefinition));
    }
    return compiled;
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

  private Map<String, Object> ensureHumanTaskConfig(FlowNode node, Map<String, Object> config) {
    Map<String, Object> compiled = new LinkedHashMap<>(config);
    Object raw = compiled.get("flowFoundryHumanTask");
    Map<String, Object> humanTask =
        raw instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
    if (!humanTask.containsKey("mode")) {
      humanTask.put("mode", "managed");
    }
    compiled.put("flowFoundryHumanTask", humanTask);
    compiled.put("nodeId", node.id());
    return compiled;
  }

  private Map<String, Object> ensureGatewayConfig(FlowNode node, Map<String, Object> config) {
    Map<String, Object> compiled = new LinkedHashMap<>(config);
    Object gatewayKind = compiled.get("gatewayKind");
    if (gatewayKind == null || String.valueOf(gatewayKind).isBlank()) {
      compiled.put("gatewayKind", "exclusive");
    }
    return compiled;
  }

  private Map<String, Object> ensureTrace(
      FlowNode node, NodeKind kind, String activityType, Map<String, Object> config) {
    if (kind == NodeKind.START || kind == NodeKind.END || kind == NodeKind.GATEWAY) {
      return config;
    }
    Map<String, Object> compiled = new LinkedHashMap<>(config);
    Map<String, Object> trace = new LinkedHashMap<>();
    trace.put("nodeId", node.id());
    trace.put("nodeName", blank(node.name()) ? node.id() : node.name().trim());
    if (!blank(node.canvasKind())) {
      trace.put("canvasKind", node.canvasKind().trim());
    }
    if (!blank(activityType)) {
      trace.put("activityType", activityType);
    } else if (kind == NodeKind.INTERMEDIATE_EVENT) {
      Object subtype = compiled.get("eventSubtype");
      trace.put("activityType", subtype == null ? "timer" : String.valueOf(subtype));
    } else if (kind == NodeKind.CHILD_WORKFLOW) {
      trace.put("activityType", "child-workflow");
    }
    compiled.put(FlowFoundryTrace.CONFIG_KEY, trace);
    return compiled;
  }

  private Map<String, Object> ensureIntermediateEventConfig(
      FlowNode node, Map<String, Object> config) {
    Map<String, Object> compiled = new LinkedHashMap<>(config);
    Object subtype = compiled.get("eventSubtype");
    if (subtype == null || String.valueOf(subtype).isBlank()) {
      compiled.put("eventSubtype", "timer");
    }
    return compiled;
  }

  private static void rejectGenericTask(FlowNode node) {
    if (node.kind() == null) {
      return;
    }
    String raw = node.kind().trim();
    if ("task".equalsIgnoreCase(raw) || "genericTask".equalsIgnoreCase(raw)) {
      throw new IllegalArgumentException(
          "Generic Task is for diagram sketching only; change it to Service Task, Human Task, Script Task, or another concrete type: "
              + node.id());
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
