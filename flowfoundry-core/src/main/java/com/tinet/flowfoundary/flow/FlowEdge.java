package com.tinet.flowfoundary.flow;

public record FlowEdge(String from, String to, Object condition, Integer priority) {

  public FlowEdge(String from, String to, Object condition) {
    this(from, to, condition, null);
  }
}
