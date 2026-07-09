package com.tinet.flowfoundry.workflow.dsl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinet.flowfoundry.flow.FlowDefinition;
import com.tinet.flowfoundry.flow.FlowEdge;
import com.tinet.flowfoundry.flow.FlowMetadata;
import com.tinet.flowfoundry.flow.FlowNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Server-side port of modeler {@code buildDslForModel}: canvas model_json → FlowDefinition DSL.
 */
@Component
public class CanvasToDslConverter {

  @FunctionalInterface
  public interface ChildDslLoader {
    FlowDefinition load(
        String childWorkflowId, String preferredVersion, Set<String> seenWorkflowIds);
  }

  private static final Set<String> NON_RUNTIME_KINDS =
      Set.of("textAnnotation", "participant", "subProcess");
  private static final Set<String> GENERIC_TASK_KINDS = Set.of("task", "genericTask");
  private static final Set<String> INTERMEDIATE_EVENT_KINDS =
      Set.of(
          "intermediateEvent",
          "intermediateCatchEvent",
          "timerEvent",
          "boundaryEvent",
          "timer");

  private final ObjectMapper objectMapper;

  public CanvasToDslConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * @param childLoader loads a child workflow DSL; may return null if missing. Receives an updated
   *     {@code seenWorkflowIds} that already includes the child id (cycle guard).
   */
  public FlowDefinition convert(
      JsonNode model, String version, Set<String> seenWorkflowIds, ChildDslLoader childLoader) {
    if (model == null || model.isNull()) {
      throw new IllegalArgumentException("workflow model is required");
    }
    assertNoGenericTaskNodes(model);
    assertCompilableRuntimeNodes(model);

    List<JsonNode> runtimeNodes = new ArrayList<>();
    Set<String> runtimeNodeIds = new HashSet<>();
    for (JsonNode node : nodesOf(model)) {
      if (isRuntimeNode(node)) {
        runtimeNodes.add(node);
        runtimeNodeIds.add(text(node, "id"));
      }
    }

    List<FlowNode> dslNodes = new ArrayList<>();
    for (JsonNode n : runtimeNodes) {
      dslNodes.add(toFlowNode(n, model, seenWorkflowIds, childLoader));
    }

    List<FlowEdge> dslEdges = new ArrayList<>();
    for (JsonNode e : edgesOf(model)) {
      String from = text(e, "from");
      String to = text(e, "to");
      if (!runtimeNodeIds.contains(from) || !runtimeNodeIds.contains(to)) {
        continue;
      }
      Object condition = e.has("condition") && !e.get("condition").isNull()
          ? objectMapper.convertValue(e.get("condition"), Object.class)
          : "default";
      if (condition == null || (condition instanceof String s && s.isBlank())) {
        condition = "default";
      }
      Integer priority =
          e.has("priority") && !e.get("priority").isNull() ? e.get("priority").asInt() : null;
      dslEdges.add(new FlowEdge(from, to, condition, priority));
    }

    String flowId = text(model, "id");
    String flowName = text(model, "name");
    Map<String, Object> inputs = readObjectMap(model.get("inputs"));
    Map<String, Object> variables = readObjectMap(model.get("variables"));

    return new FlowDefinition(
        "1.0",
        new FlowMetadata(flowId, flowName, version),
        inputs,
        variables,
        dslNodes,
        dslEdges);
  }

  private FlowNode toFlowNode(
      JsonNode n, JsonNode model, Set<String> seenWorkflowIds, ChildDslLoader childLoader) {
    String kind = text(n, "kind");
    Map<String, Object> config = runtimeConfig(n, model, seenWorkflowIds, childLoader);
    return new FlowNode(
        text(n, "id"),
        executionNodeKind(kind),
        firstNonBlank(text(n, "name"), text(n, "id")),
        kind,
        resolveActivityType(n),
        null,
        textOrNull(n, "timeout"),
        n.has("maxAttempts") && !n.get("maxAttempts").isNull() ? n.get("maxAttempts").asInt() : null,
        textOrNull(n, "scriptCodeId"),
        textOrNull(n, "scriptVersion"),
        textOrNull(n, "scriptName"),
        readStringList(n.get("inputArgs")),
        readStringMap(n.get("inputMapping")),
        readStringMap(n.get("outputMapping")),
        textOrNull(n, "inputMappingMode"),
        config);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> runtimeConfig(
      JsonNode n, JsonNode model, Set<String> seenWorkflowIds, ChildDslLoader childLoader) {
    Map<String, Object> config = new LinkedHashMap<>();
    JsonNode rawConfig = n.get("config");
    if (rawConfig != null && rawConfig.isObject()) {
      config.putAll(objectMapper.convertValue(rawConfig, Map.class));
    }

    String participantId = textOrNull(n, "participantId");
    JsonNode participant = participantById(participantId, model);
    if (participant != null) {
      Map<String, Object> participantConfig = new LinkedHashMap<>();
      participantConfig.put("participantId", text(participant, "id"));
      JsonNode pConfig = participant.get("config");
      String participantRef =
          pConfig != null && pConfig.has("participantRef")
              ? text(pConfig, "participantRef")
              : "";
      participantConfig.put("participantRef", participantRef);
      participantConfig.put(
          "name", firstNonBlank(text(participant, "name"), text(participant, "id")));
      config.put("flowFoundryParticipant", participantConfig);
    }

    String gatewayKind = canvasGatewayKind(text(n, "kind"));
    if (gatewayKind != null) {
      config.put("gatewayKind", gatewayKind);
    }

    String kind = text(n, "kind");
    if ("startEvent".equals(kind) || "start".equals(kind)) {
      String subtype = startEventSubtype(config);
      config.put("startEventSubtype", subtype);
      if (!"timer".equals(subtype)) {
        config.remove("timerDefinition");
      }
    }

    if (INTERMEDIATE_EVENT_KINDS.contains(kind)) {
      config.put("eventSubtype", resolveEventSubtype(config));
      Object timerDef = config.get("timerDefinition");
      if (timerDef instanceof Map<?, ?> timerMap) {
        Object type = timerMap.get("type");
        String timerType = type == null ? "duration" : String.valueOf(type);
        Object value = timerMap.get("value");
        if (value != null && "duration".equals(timerType)) {
          config.put("duration", normalizeTimerDuration(String.valueOf(value)));
        }
      }
    }

    if ("workflow".equals(kind)) {
      String childWorkflowId = stringOrEmpty(config.get("childWorkflowId"));
      String childWorkflowVersion = stringOrEmpty(config.get("childWorkflowVersion"));
      if (childWorkflowVersion.isBlank()) {
        childWorkflowVersion = "latest";
      }
      Map<String, Object> childMeta = new LinkedHashMap<>();
      childMeta.put("childWorkflowId", childWorkflowId);
      childMeta.put("childWorkflowVersion", childWorkflowVersion);
      childMeta.put("name", stringOrEmpty(config.get("childWorkflowName")));
      config.put("flowFoundryChildWorkflow", childMeta);

      FlowDefinition childDefinition =
          resolveChildWorkflowDefinition(
              childWorkflowId, childWorkflowVersion, seenWorkflowIds, childLoader);
      if (childDefinition != null) {
        config.put("childWorkflowDefinition", childDefinition);
      }
    }

    if ("humanTask".equals(kind) || "userTask".equals(kind)) {
      Map<String, Object> humanTask = new LinkedHashMap<>();
      Object existing = config.get("flowFoundryHumanTask");
      String mode = "managed";
      if (existing instanceof Map<?, ?> existingMap && existingMap.get("mode") != null) {
        mode = String.valueOf(existingMap.get("mode"));
      }
      humanTask.put("mode", mode);
      config.put("flowFoundryHumanTask", humanTask);
      config.put("nodeId", text(n, "id"));
    }

    Map<String, Object> loop = buildFlowFoundryLoop(n, config);
    if (loop != null) {
      config.put("flowFoundryLoop", loop);
    }

    mergeTaskHeaders(config, n);
    return config;
  }

  private FlowDefinition resolveChildWorkflowDefinition(
      String childWorkflowId,
      String preferredVersion,
      Set<String> seenWorkflowIds,
      ChildDslLoader childLoader) {
    if (childWorkflowId == null || childWorkflowId.isBlank()) {
      return null;
    }
    if (seenWorkflowIds.contains(childWorkflowId)) {
      return null;
    }
    if (childLoader == null) {
      return null;
    }
    Set<String> nextSeen = new HashSet<>(seenWorkflowIds);
    nextSeen.add(childWorkflowId);
    return childLoader.load(childWorkflowId, preferredVersion, nextSeen);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> buildFlowFoundryLoop(JsonNode n, Map<String, Object> config) {
    String loop = textOrNull(n, "loop");
    if (loop == null || loop.isBlank() || "none".equals(loop)) {
      return null;
    }
    Map<String, Object> stored = new LinkedHashMap<>();
    Object existing = config.get("flowFoundryLoop");
    if (existing instanceof Map<?, ?> existingMap) {
      stored.putAll((Map<String, Object>) existingMap);
    }
    String mode = "standardLoop".equals(loop) ? "standard" : "multiInstance";
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("mode", mode);
    result.put(
        "maxIterations",
        stored.get("maxIterations") instanceof Number num ? num.intValue() : 100);
    Object sequential = stored.get("sequential");
    result.put("sequential", sequential == null || Boolean.TRUE.equals(sequential));
    if ("standard".equals(mode)) {
      result.put("condition", stringOrEmpty(stored.get("condition")));
      result.put(
          "iterationVar",
          firstNonBlank(stringOrEmpty(stored.get("iterationVar")), "loop.iteration"));
    } else {
      result.put("collection", stringOrEmpty(stored.get("collection")));
      result.put(
          "elementVar", firstNonBlank(stringOrEmpty(stored.get("elementVar")), "loop.item"));
      result.put("indexVar", firstNonBlank(stringOrEmpty(stored.get("indexVar")), "loop.index"));
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private void mergeTaskHeaders(Map<String, Object> config, JsonNode n) {
    Map<String, Object> headers = resolveTaskHeaders(n, config);
    if (headers != null && !headers.isEmpty()) {
      config.put("taskHeaders", headers);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> resolveTaskHeaders(JsonNode n, Map<String, Object> config) {
    Map<String, Object> fromConfig = null;
    Object configHeaders = config.get("taskHeaders");
    if (configHeaders instanceof Map<?, ?> map && !map.isEmpty()) {
      fromConfig = new LinkedHashMap<>((Map<String, Object>) map);
    }
    Map<String, Object> fromLegacy = null;
    JsonNode legacy = n.get("headers");
    if (legacy != null && legacy.isObject() && legacy.size() > 0) {
      fromLegacy = objectMapper.convertValue(legacy, Map.class);
    }
    if (fromConfig != null) {
      if (fromLegacy != null) {
        Map<String, Object> merged = new LinkedHashMap<>(fromLegacy);
        merged.putAll(fromConfig);
        return merged;
      }
      return fromConfig;
    }
    return fromLegacy;
  }

  private static String startEventSubtype(Map<String, Object> config) {
    Object raw = config.get("startEventSubtype");
    if (raw == null || String.valueOf(raw).trim().isEmpty()) {
      return "none";
    }
    return String.valueOf(raw).trim().toLowerCase();
  }

  private static String resolveEventSubtype(Map<String, Object> config) {
    Object subtype = config.get("subtype");
    if (subtype != null && !String.valueOf(subtype).isBlank()) {
      return String.valueOf(subtype);
    }
    Object timerDef = config.get("timerDefinition");
    if (timerDef instanceof Map<?, ?> timerMap && timerMap.get("value") != null) {
      return "timer";
    }
    return "timer";
  }

  private static String normalizeTimerDuration(String value) {
    String text = value == null ? "" : value.trim();
    if (text.startsWith("${")) {
      return text;
    }
    if (text.matches("^\\d+[smh]$")) {
      return text;
    }
    if (text.matches("^\\d+M$")) {
      return text.toLowerCase();
    }
    return text.isEmpty() ? "1m" : text;
  }

  private static String resolveActivityType(JsonNode n) {
    String activityType = textOrNull(n, "activityType");
    if (activityType != null && !activityType.isBlank()) {
      return activityType;
    }
    String kind = text(n, "kind");
    if ("scriptTask".equals(kind)) {
      return "script-runtime";
    }
    if ("humanTask".equals(kind) || "userTask".equals(kind)) {
      return "human-task";
    }
    return activityType;
  }

  private static String executionNodeKind(String kind) {
    return switch (kind) {
      case "startEvent", "start" -> "START";
      case "endEvent", "end" -> "END";
      case "serviceTask", "scriptTask", "activity" -> "ACTIVITY";
      case "workflow", "childWorkflow", "callActivity" -> "CHILD_WORKFLOW";
      case "humanTask", "userTask" -> "ACTIVITY";
      case "exclusiveGateway", "inclusiveGateway", "parallelGateway", "eventBasedGateway" ->
          "GATEWAY";
      case "intermediateEvent",
          "intermediateCatchEvent",
          "timerEvent",
          "boundaryEvent",
          "timer" -> "INTERMEDIATE_EVENT";
      default -> kind;
    };
  }

  private static String canvasGatewayKind(String kind) {
    return switch (kind) {
      case "exclusiveGateway" -> "exclusive";
      case "parallelGateway" -> "parallel";
      case "inclusiveGateway" -> "inclusive";
      case "eventBasedGateway" -> "eventBased";
      default -> null;
    };
  }

  private static boolean isRuntimeNode(JsonNode n) {
    return !NON_RUNTIME_KINDS.contains(text(n, "kind"));
  }

  private static void assertNoGenericTaskNodes(JsonNode model) {
    List<String> names = new ArrayList<>();
    for (JsonNode n : nodesOf(model)) {
      if (isRuntimeNode(n) && GENERIC_TASK_KINDS.contains(text(n, "kind"))) {
        names.add(firstNonBlank(text(n, "name"), text(n, "id")));
      }
    }
    if (!names.isEmpty()) {
      throw new IllegalArgumentException(
          "Generic task nodes are not compilable: " + String.join(", ", names));
    }
  }

  private static void assertCompilableRuntimeNodes(JsonNode model) {
    List<JsonNode> allNodes = nodesOf(model);
    long runtimeCount = allNodes.stream().filter(CanvasToDslConverter::isRuntimeNode).count();
    if (runtimeCount > 0) {
      return;
    }
    String flowName = firstNonBlank(text(model, "name"), text(model, "id"), "workflow");
    if (allNodes.isEmpty()) {
      throw new IllegalArgumentException(
          "Workflow has no runtime nodes to compile: " + flowName);
    }
    Map<String, Integer> kindCounts = new HashMap<>();
    for (JsonNode node : allNodes) {
      String kind = firstNonBlank(text(node, "kind"), "unknown");
      kindCounts.merge(kind, 1, Integer::sum);
    }
    StringBuilder kinds = new StringBuilder();
    kindCounts.forEach(
        (kind, count) -> {
          if (kinds.length() > 0) {
            kinds.append(", ");
          }
          kinds.append(kind).append("×").append(count);
        });
    throw new IllegalArgumentException(
        "Workflow has no compilable runtime nodes ("
            + allNodes.size()
            + " nodes excluded): "
            + flowName
            + " ["
            + kinds
            + "]");
  }

  private static JsonNode participantById(String id, JsonNode model) {
    if (id == null || id.isBlank()) {
      return null;
    }
    for (JsonNode node : nodesOf(model)) {
      if (id.equals(text(node, "id")) && "participant".equals(text(node, "kind"))) {
        return node;
      }
    }
    return null;
  }

  private static List<JsonNode> nodesOf(JsonNode model) {
    JsonNode nodes = model.get("nodes");
    if (nodes == null || !nodes.isArray()) {
      return List.of();
    }
    List<JsonNode> list = new ArrayList<>();
    nodes.forEach(list::add);
    return list;
  }

  private static List<JsonNode> edgesOf(JsonNode model) {
    JsonNode edges = model.get("edges");
    if (edges == null || !edges.isArray()) {
      return List.of();
    }
    List<JsonNode> list = new ArrayList<>();
    edges.forEach(list::add);
    return list;
  }

  private Map<String, Object> readObjectMap(JsonNode node) {
    if (node == null || !node.isObject()) {
      return Map.of();
    }
    return objectMapper.convertValue(node, Map.class);
  }

  private static List<String> readStringList(JsonNode node) {
    if (node == null || !node.isArray()) {
      return null;
    }
    List<String> list = new ArrayList<>();
    for (JsonNode item : node) {
      list.add(item.asText());
    }
    return list;
  }

  private static Map<String, String> readStringMap(JsonNode node) {
    if (node == null || !node.isObject()) {
      return null;
    }
    Map<String, String> map = new LinkedHashMap<>();
    Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      map.put(entry.getKey(), entry.getValue().asText());
    }
    return map;
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    return value == null || value.isNull() ? "" : value.asText();
  }

  private static String textOrNull(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    String text = value.asText();
    return text.isBlank() ? null : text;
  }

  private static String stringOrEmpty(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return "";
    }
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "";
  }
}
