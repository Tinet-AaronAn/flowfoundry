package com.tinet.flowfoundary.interpreter.model;

import java.util.List;
import java.util.Map;

public record ExecutionPlan(
    String flowId,
    String version,
    String startNodeId,
    Map<String, ExecutionNode> nodes,
    Map<String, List<ExecutionEdge>> edges) {

  public ExecutionNode startNode() {
    return requireNode(startNodeId);
  }

  public ExecutionNode requireNode(String nodeId) {
    if (nodes == null || !nodes.containsKey(nodeId)) {
      throw new IllegalArgumentException("Unknown execution node: " + nodeId);
    }
    return nodes.get(nodeId);
  }

  public List<ExecutionEdge> outgoingEdges(String nodeId) {
    if (edges == null) {
      return List.of();
    }
    return edges.getOrDefault(nodeId, List.of());
  }
}
