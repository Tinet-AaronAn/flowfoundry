package com.tinet.flowfoundry.interpreter.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record ExecutionEdge(String target, Object condition) {

  @JsonIgnore
  public boolean isDefault() {
    return condition == null
        || (condition instanceof String text
            && (text.isBlank() || "default".equalsIgnoreCase(text.trim())));
  }
}
