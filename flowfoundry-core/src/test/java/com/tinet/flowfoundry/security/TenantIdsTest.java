package com.tinet.flowfoundry.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TenantIdsTest {

  @Test
  void normalizesValidTenantId() {
    assertThat(TenantIds.normalize(" tenant-a_1 ")).isEqualTo("tenant-a_1");
  }

  @Test
  void rejectsBlankTenantId() {
    assertThatThrownBy(() -> TenantIds.normalize(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsInvalidCharacters() {
    assertThatThrownBy(() -> TenantIds.normalize("tenant/a"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
