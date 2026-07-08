package com.tinet.flowfoundry.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NamespaceIdsTest {

  @Test
  void normalizesValidNamespace() {
    assertThat(NamespaceIds.normalize(" ns-a_1 ")).isEqualTo("ns-a_1");
  }

  @Test
  void rejectsBlankNamespace() {
    assertThatThrownBy(() -> NamespaceIds.normalize(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsInvalidCharacters() {
    assertThatThrownBy(() -> NamespaceIds.normalize("ns/a"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
