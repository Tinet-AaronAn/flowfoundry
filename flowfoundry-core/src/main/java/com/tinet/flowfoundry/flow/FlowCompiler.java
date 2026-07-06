package com.tinet.flowfoundry.flow;

import com.tinet.flowfoundry.interpreter.model.ExecutionEdge;
import com.tinet.flowfoundry.interpreter.model.ExecutionNode;
import com.tinet.flowfoundry.interpreter.model.ExecutionPlan;
import com.tinet.flowfoundry.interpreter.model.FlowFoundryTrace;
import com.tinet.flowfoundry.interpreter.model.NodeKind;
import com.tinet.flowfoundry.activity.ActivityTypes;
import com.tinet.flowfoundry.registry.ActivityRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
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

    Map<String, Map<String, Object>> gatewayConfigPatches =
        GatewayTopologyValidator.validateAndEnrich(definition.nodes(), definition.edges());

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
      ExecutionNode executionNode = compileNode(node, kind, gatewayConfigPatches);
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

  private ExecutionNode compileNode(
      FlowNode node, NodeKind kind, Map<String, Map<String, Object>> gatewayConfigPatches) {
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
      if (!blank(node.scriptCodeId())) {
        config = new LinkedHashMap<>(config);
        config.put("scriptCodeId", node.scriptCodeId());
        config.put("scriptVersion", blank(node.scriptVersion()) ? "latest" : node.scriptVersion());
        if (!blank(node.scriptName())) {
          config.put("scriptName", node.scriptName());
        }
      }
      if (ActivityTypes.isHumanTaskActivity(activityType)) {
        config = ensureHumanTaskConfig(node, config);
      }
      config = ensureLoopConfig(node, config);
    }
    if (kind != NodeKind.ACTIVITY) {
      LoopDefinition loop = LoopDefinition.fromConfig(config);
      if (loop.isEnabled()) {
        throw new IllegalArgumentException(
            "Loop is only supported on ACTIVITY nodes: " + node.id());
      }
    }
    if (kind == NodeKind.CHILD_WORKFLOW) {
      config = compileChildWorkflowConfig(node, config);
    }
    if (kind == NodeKind.GATEWAY) {
      config = ensureGatewayConfig(node, config, gatewayConfigPatches);
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

  private Map<String, Object> ensureLoopConfig(FlowNode node, Map<String, Object> config) {
    LoopDefinition loop = LoopDefinition.fromConfig(config);
    if (!loop.isEnabled()) {
      return config;
    }
    loop.validate(node.id());
    Map<String, Object> compiled = new LinkedHashMap<>(config);
    Map<String, Object> loopConfig = new LinkedHashMap<>();
    loopConfig.put("mode", loop.mode());
    if (loop.isStandard()) {
      loopConfig.put("condition", loop.condition());
      loopConfig.put("iterationVar", loop.iterationVar());
    }
    if (loop.isMultiInstance()) {
      loopConfig.put("collection", loop.collection());
      loopConfig.put("elementVar", loop.elementVar());
      loopConfig.put("indexVar", loop.indexVar());
      loopConfig.put("sequential", loop.sequential());
    }
    loopConfig.put("maxIterations", loop.maxIterations());
    compiled.put(LoopDefinition.CONFIG_KEY, loopConfig);
    return compiled;
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
    List<IndexedFlowEdge> indexed = new ArrayList<>();
    for (int index = 0; index < flowEdges.size(); index++) {
      FlowEdge edge = flowEdges.get(index);
      if (edge == null || blank(edge.from()) || blank(edge.to())) {
        throw new IllegalArgumentException("Every edge requires from and to");
      }
      if (!nodes.containsKey(edge.from())) {
        throw new IllegalArgumentException("Edge source does not exist: " + edge.from());
      }
      if (!nodes.containsKey(edge.to())) {
        throw new IllegalArgumentException("Edge target does not exist: " + edge.to());
      }
      indexed.add(new IndexedFlowEdge(edge, index));
    }
    validateOutgoingEdges(indexed, nodes);

    Map<String, List<IndexedFlowEdge>> grouped = new LinkedHashMap<>();
    for (IndexedFlowEdge item : indexed) {
      grouped.computeIfAbsent(item.edge().from(), ignored -> new ArrayList<>()).add(item);
    }
    for (Map.Entry<String, List<IndexedFlowEdge>> entry : grouped.entrySet()) {
      List<IndexedFlowEdge> outgoing = entry.getValue();
      outgoing.sort(
          Comparator.comparing(
                  (IndexedFlowEdge item) ->
                      item.edge().priority() == null ? Integer.MAX_VALUE : item.edge().priority())
              .thenComparingInt(IndexedFlowEdge::index));
      List<ExecutionEdge> compiled = new ArrayList<>();
      for (IndexedFlowEdge item : outgoing) {
        compiled.add(
            new ExecutionEdge(item.edge().to(), safeFeelCompiler.compile(item.edge().condition())));
      }
      edges.put(entry.getKey(), compiled);
    }
    return edges;
  }

  private void validateOutgoingEdges(
      List<IndexedFlowEdge> indexed, Map<String, ExecutionNode> nodes) {
    Map<String, List<FlowEdge>> byFrom = new LinkedHashMap<>();
    for (IndexedFlowEdge item : indexed) {
      byFrom.computeIfAbsent(item.edge().from(), ignored -> new ArrayList<>()).add(item.edge());
    }
    for (Map.Entry<String, List<FlowEdge>> entry : byFrom.entrySet()) {
      ExecutionNode node = nodes.get(entry.getKey());
      if (node == null || node.requiredKind() != NodeKind.ACTIVITY) {
        continue;
      }
      if (entry.getValue().size() > 1) {
        throw new IllegalArgumentException(
            "Activity node allows at most one outgoing edge: " + entry.getKey());
      }
      for (FlowEdge edge : entry.getValue()) {
        if (!isDefaultCondition(edge.condition())) {
          throw new IllegalArgumentException(
              "Activity outgoing edge must not have a condition; insert a Gateway for branching: "
                  + entry.getKey()
                  + " -> "
                  + edge.to());
        }
      }
    }
  }

  private static boolean isDefaultCondition(Object condition) {
    if (condition == null) {
      return true;
    }
    if (condition instanceof String text) {
      return text.isBlank() || "default".equalsIgnoreCase(text.trim());
    }
    return false;
  }

  private record IndexedFlowEdge(FlowEdge edge, int index) {}

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

  private Map<String, Object> ensureGatewayConfig(
      FlowNode node,
      Map<String, Object> config,
      Map<String, Map<String, Object>> gatewayConfigPatches) {
    Map<String, Object> compiled = new LinkedHashMap<>(config);
    Object gatewayKind = compiled.get("gatewayKind");
    if (gatewayKind == null || String.valueOf(gatewayKind).isBlank()) {
      compiled.put("gatewayKind", "exclusive");
    }
    Map<String, Object> patch = gatewayConfigPatches.get(node.id());
    if (patch != null) {
      compiled.putAll(patch);
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
