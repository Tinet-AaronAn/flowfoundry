package com.tinet.flowfoundry.flow;

import java.util.List;
import java.util.Map;

public record FlowDefinition(
    String dslVersion,
    FlowMetadata flow,
    Map<String, Object> inputs,
    Map<String, Object> variables,
    List<FlowNode> nodes,
    List<FlowEdge> edges) {}
