package com.tinet.flowfoundary.flow;

import com.tinet.flowfoundary.interpreter.model.NodeKind;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/** Validates gateway topology and produces config enrichments for {@link FlowCompiler}. */
final class GatewayTopologyValidator {

  private GatewayTopologyValidator() {}

  static Map<String, Map<String, Object>> validateAndEnrich(
      List<FlowNode> flowNodes, List<FlowEdge> flowEdges) {
    Map<String, FlowNode> nodesById = new LinkedHashMap<>();
    for (FlowNode node : flowNodes) {
      if (node != null && node.id() != null) {
        nodesById.put(node.id(), node);
      }
    }
    Map<String, List<FlowEdge>> outgoing = new LinkedHashMap<>();
    Map<String, List<FlowEdge>> incoming = new LinkedHashMap<>();
    for (FlowEdge edge : flowEdges) {
      if (edge == null) {
        continue;
      }
      outgoing.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
      incoming.computeIfAbsent(edge.to(), ignored -> new ArrayList<>()).add(edge);
    }

    Map<String, Map<String, Object>> patches = new LinkedHashMap<>();
    for (FlowNode node : flowNodes) {
      if (node == null || !NodeKind.GATEWAY.name().equalsIgnoreCase(node.kind())) {
        continue;
      }
      String gatewayKind = gatewayKind(node);
      int inCount = incoming.getOrDefault(node.id(), List.of()).size();
      int outCount = outgoing.getOrDefault(node.id(), List.of()).size();
      validateGatewayCounts(node.id(), gatewayKind, inCount, outCount);

      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("gatewayKind", gatewayKind);
      if ("parallel".equals(gatewayKind) || "inclusive".equals(gatewayKind)) {
        String role = inCount == 1 && outCount >= 2 ? "split" : "join";
        patch.put("gatewayRole", role);
        if ("split".equals(role)) {
          validateSplitEdges(node.id(), gatewayKind, outgoing.get(node.id()));
        }
      } else if ("eventBased".equals(gatewayKind)) {
        patch.put("gatewayRole", "split");
        validateEventBasedSplit(node.id(), outgoing.get(node.id()), nodesById);
      } else {
        patch.put("gatewayRole", outCount >= 2 ? "split" : "pass");
      }
      patches.put(node.id(), patch);
    }

    pairSplitJoin(nodesById, outgoing, incoming, patches);
    detectNestedParallel(nodesById, outgoing, incoming, patches);
    validateParallelOutputMappings(nodesById, outgoing, patches);
    return patches;
  }

  private static void validateGatewayCounts(
      String nodeId, String gatewayKind, int inCount, int outCount) {
    if ("parallel".equals(gatewayKind) || "inclusive".equals(gatewayKind)) {
      if (inCount >= 2 && outCount >= 2) {
        throw new IllegalArgumentException(
            "Gateway cannot be both split and join: " + nodeId);
      }
      if (inCount == 1 && outCount < 2) {
        throw new IllegalArgumentException(
            "Parallel/Inclusive split gateway requires at least two outgoing edges: " + nodeId);
      }
      if (outCount == 1 && inCount < 2) {
        throw new IllegalArgumentException(
            "Parallel/Inclusive join gateway requires at least two incoming edges: " + nodeId);
      }
    }
    if ("eventBased".equals(gatewayKind)) {
      if (inCount != 1 || outCount < 2) {
        throw new IllegalArgumentException(
            "Event-based gateway requires one incoming and at least two outgoing edges: "
                + nodeId);
      }
    }
  }

  private static void validateSplitEdges(
      String nodeId, String gatewayKind, List<FlowEdge> edges) {
    if (edges == null) {
      return;
    }
    // Parallel and Inclusive split edges may carry FEEL conditions.
  }

  private static void validateEventBasedSplit(
      String nodeId, List<FlowEdge> edges, Map<String, FlowNode> nodesById) {
    if (edges == null) {
      return;
    }
    for (FlowEdge edge : edges) {
      FlowNode first = nodesById.get(edge.to());
      if (first == null || !NodeKind.INTERMEDIATE_EVENT.name().equalsIgnoreCase(first.kind())) {
        throw new IllegalArgumentException(
            "Event-based gateway outgoing edge must target an Intermediate Event first: "
                + nodeId
                + " -> "
                + edge.to());
      }
    }
  }

  private static void pairSplitJoin(
      Map<String, FlowNode> nodesById,
      Map<String, List<FlowEdge>> outgoing,
      Map<String, List<FlowEdge>> incoming,
      Map<String, Map<String, Object>> patches) {
    for (Map.Entry<String, Map<String, Object>> entry : patches.entrySet()) {
      String nodeId = entry.getKey();
      Map<String, Object> patch = entry.getValue();
      String gatewayKind = String.valueOf(patch.get("gatewayKind"));
      if (!"parallel".equals(gatewayKind) && !"inclusive".equals(gatewayKind)) {
        continue;
      }
      if (!"split".equals(patch.get("gatewayRole"))) {
        continue;
      }
      List<FlowEdge> splitEdges = outgoing.get(nodeId);
      String joinId =
          findPairedJoin(nodeId, gatewayKind, splitEdges, outgoing, incoming, nodesById);
      if (joinId == null) {
        throw new IllegalArgumentException(
            gatewayKind + " split gateway is not closed by a matching join: " + nodeId);
      }
      patch.put("pairedJoinId", joinId);
      Map<String, Object> joinPatch = patches.computeIfAbsent(joinId, ignored -> new LinkedHashMap<>());
      joinPatch.put("gatewayKind", gatewayKind);
      joinPatch.put("gatewayRole", "join");
      joinPatch.put("pairedSplitId", nodeId);
      if ("parallel".equals(gatewayKind)) {
        joinPatch.put("expectedBranchCount", splitEdges.size());
      }
    }
  }

  private static String findPairedJoin(
      String splitId,
      String gatewayKind,
      List<FlowEdge> splitEdges,
      Map<String, List<FlowEdge>> outgoing,
      Map<String, List<FlowEdge>> incoming,
      Map<String, FlowNode> nodesById) {
    if (splitEdges == null || splitEdges.isEmpty()) {
      return null;
    }
    Set<String> candidateJoins = null;
    for (FlowEdge edge : splitEdges) {
      Set<String> joinsOnPath =
          joinsReachableFrom(edge.to(), splitId, gatewayKind, outgoing, incoming, nodesById);
      if (candidateJoins == null) {
        candidateJoins = joinsOnPath;
      } else {
        candidateJoins = intersection(candidateJoins, joinsOnPath);
      }
    }
    if (candidateJoins == null || candidateJoins.isEmpty()) {
      return null;
    }
    if (candidateJoins.size() > 1) {
      throw new IllegalArgumentException(
          "Ambiguous " + gatewayKind + " join for split gateway " + splitId + ": " + candidateJoins);
    }
    return candidateJoins.iterator().next();
  }

  private static Set<String> joinsReachableFrom(
      String startId,
      String splitId,
      String gatewayKind,
      Map<String, List<FlowEdge>> outgoing,
      Map<String, List<FlowEdge>> incoming,
      Map<String, FlowNode> nodesById) {
    Set<String> joins = new LinkedHashSet<>();
    Set<String> visited = new HashSet<>();
    Queue<String> queue = new ArrayDeque<>();
    queue.add(startId);
    while (!queue.isEmpty()) {
      String current = queue.poll();
      if (!visited.add(current)) {
        continue;
      }
      FlowNode node = nodesById.get(current);
      if (node != null && NodeKind.GATEWAY.name().equalsIgnoreCase(node.kind())) {
        String kind = gatewayKind(node);
        if (gatewayKind.equals(kind) && isJoinGateway(current, incoming, outgoing)) {
          joins.add(current);
          continue;
        }
        if ("parallel".equals(gatewayKind)
            && gatewayKind.equals(kind)
            && isSplitGateway(current, incoming, outgoing)) {
          throw new IllegalArgumentException(
              "Nested parallel gateway between split " + splitId + " and join: " + current);
        }
      }
      for (FlowEdge edge : outgoing.getOrDefault(current, List.of())) {
        queue.add(edge.to());
      }
    }
    return joins;
  }

  private static void detectNestedParallel(
      Map<String, FlowNode> nodesById,
      Map<String, List<FlowEdge>> outgoing,
      Map<String, List<FlowEdge>> incoming,
      Map<String, Map<String, Object>> patches) {
    for (Map.Entry<String, Map<String, Object>> entry : patches.entrySet()) {
      if (!"parallel".equals(String.valueOf(entry.getValue().get("gatewayKind")))) {
        continue;
      }
      if (!"split".equals(entry.getValue().get("gatewayRole"))) {
        continue;
      }
      for (FlowEdge edge : outgoing.getOrDefault(entry.getKey(), List.of())) {
        joinsReachableFrom(edge.to(), entry.getKey(), "parallel", outgoing, incoming, nodesById);
      }
    }
  }

  private static void validateParallelOutputMappings(
      Map<String, FlowNode> nodesById,
      Map<String, List<FlowEdge>> outgoing,
      Map<String, Map<String, Object>> patches) {
    for (Map.Entry<String, Map<String, Object>> entry : patches.entrySet()) {
      String gatewayKind = String.valueOf(entry.getValue().get("gatewayKind"));
      if (!"parallel".equals(gatewayKind) && !"inclusive".equals(gatewayKind)) {
        continue;
      }
      if (!"split".equals(entry.getValue().get("gatewayRole"))) {
        continue;
      }
      String joinId = String.valueOf(entry.getValue().get("pairedJoinId"));
      Set<String> activityIds = activitiesBetween(entry.getKey(), joinId, outgoing, nodesById);
      Map<String, String> keyToNode = new LinkedHashMap<>();
      for (String activityId : activityIds) {
        FlowNode activity = nodesById.get(activityId);
        if (activity == null || activity.outputMapping() == null) {
          continue;
        }
        for (String target : activity.outputMapping().keySet()) {
          String normalized = normalizeMappingTarget(target);
          if (keyToNode.containsKey(normalized)) {
            throw new IllegalArgumentException(
                "Parallel region has overlapping output mapping key '"
                    + normalized
                    + "' on nodes "
                    + keyToNode.get(normalized)
                    + " and "
                    + activityId);
          }
          keyToNode.put(normalized, activityId);
        }
      }
    }
  }

  private static Set<String> activitiesBetween(
      String splitId,
      String joinId,
      Map<String, List<FlowEdge>> outgoing,
      Map<String, FlowNode> nodesById) {
    Set<String> activities = new LinkedHashSet<>();
    Set<String> visited = new HashSet<>();
    Queue<String> queue = new ArrayDeque<>();
    for (FlowEdge edge : outgoing.getOrDefault(splitId, List.of())) {
      queue.add(edge.to());
    }
    while (!queue.isEmpty()) {
      String current = queue.poll();
      if (joinId.equals(current) || !visited.add(current)) {
        continue;
      }
      FlowNode node = nodesById.get(current);
      if (node != null && NodeKind.ACTIVITY.name().equalsIgnoreCase(node.kind())) {
        activities.add(current);
      }
      for (FlowEdge edge : outgoing.getOrDefault(current, List.of())) {
        if (!joinId.equals(edge.to())) {
          queue.add(edge.to());
        }
      }
    }
    return activities;
  }

  private static boolean isSplitGateway(
      String nodeId, Map<String, List<FlowEdge>> incoming, Map<String, List<FlowEdge>> outgoing) {
    return incoming.getOrDefault(nodeId, List.of()).size() == 1
        && outgoing.getOrDefault(nodeId, List.of()).size() >= 2;
  }

  private static boolean isJoinGateway(
      String nodeId, Map<String, List<FlowEdge>> incoming, Map<String, List<FlowEdge>> outgoing) {
    return incoming.getOrDefault(nodeId, List.of()).size() >= 2
        && outgoing.getOrDefault(nodeId, List.of()).size() == 1;
  }

  private static Set<String> intersection(Set<String> left, Set<String> right) {
    Set<String> result = new LinkedHashSet<>();
    for (String value : left) {
      if (right.contains(value)) {
        result.add(value);
      }
    }
    return result;
  }

  private static String gatewayKind(FlowNode node) {
    if (node.config() == null) {
      return "exclusive";
    }
    Object raw = node.config().get("gatewayKind");
    if (raw == null || String.valueOf(raw).isBlank()) {
      return "exclusive";
    }
    return String.valueOf(raw);
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

  private static String normalizeMappingTarget(String target) {
    if (target == null) {
      return "";
    }
    String value = target.trim();
    if (value.startsWith("$.vars.")) {
      return value.substring("$.vars.".length());
    }
    return value;
  }
}
