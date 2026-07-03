package com.tinet.flowfoundary.interpreter.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NodeKindTest {

  @Test
  void fromParsesGatewayKind() {
    assertThat(NodeKind.from("GATEWAY")).isEqualTo(NodeKind.GATEWAY);
    assertThat(NodeKind.from("gateway")).isEqualTo(NodeKind.GATEWAY);
  }

  @Test
  void fromParsesIntermediateEventAndLegacyTimerAlias() {
    assertThat(NodeKind.from("INTERMEDIATE_EVENT")).isEqualTo(NodeKind.INTERMEDIATE_EVENT);
    assertThat(NodeKind.from("TIMER")).isEqualTo(NodeKind.INTERMEDIATE_EVENT);
  }

  @Test
  void fromRejectsRemovedKinds() {
    assertThatThrownBy(() -> NodeKind.from("DECISION"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> NodeKind.from("SCRIPT_TASK"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
