package com.tinet.flowfoundry.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CycleTimerExpressionTest {

  @Test
  void parsesSimpleRepeatingInterval() {
    CycleTimerExpression.Parsed parsed = CycleTimerExpression.parse("R/PT1H", "Start");
    assertThat(parsed.repeatCount()).isNull();
    assertThat(parsed.startText()).isNull();
    assertThat(parsed.intervalMs()).isEqualTo(3_600_000L);
  }

  @Test
  void parsesLimitedRepeatCount() {
    CycleTimerExpression.Parsed parsed = CycleTimerExpression.parse("R3/PT10M", "Start");
    assertThat(parsed.repeatCount()).isEqualTo(3);
    assertThat(parsed.intervalMs()).isEqualTo(600_000L);
  }

  @Test
  void rejectsNonCycleValue() {
    assertThatThrownBy(() -> CycleTimerExpression.parse("PT1H", "Start"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid timer cycle");
  }
}
