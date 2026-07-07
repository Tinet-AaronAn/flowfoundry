package com.tinet.flowfoundry.interpreter.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tinet.flowfoundry.interpreter.model.ExecutionNode;
import com.tinet.flowfoundry.interpreter.model.NodeKind;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TimerEvaluatorTest {

  @Test
  void parsesDurationLiteral() {
    ExecutionNode node =
        timerNode(Map.of("timerDefinition", Map.of("type", "duration", "value", "5m")));
    long now = System.currentTimeMillis();

    assertThat(TimerEvaluator.evaluate(node, new VariableStore(Map.of()), now).delayMs())
        .isEqualTo(300_000L);
  }

  @Test
  void resolvesDurationVariable() {
    ExecutionNode node =
        timerNode(Map.of("timerDefinition", Map.of("type", "duration", "value", "${waitMs}")));
    VariableStore variables = new VariableStore(Map.of("waitMs", "1500ms"));

    assertThat(TimerEvaluator.evaluate(node, variables, System.currentTimeMillis()).delayMs())
        .isEqualTo(1500L);
  }

  @Test
  void resolvesDateVariableWithTimezone() {
    ZonedDateTime target =
        ZonedDateTime.of(2030, 1, 15, 9, 30, 0, 0, ZoneId.of("Asia/Shanghai"));
    ExecutionNode node =
        timerNode(
            Map.of(
                "eventSubtype",
                "timer",
                "timerDefinition",
                Map.of(
                    "type",
                    "date",
                    "value",
                    "${slot.fixedTime}",
                    "timezone",
                    "${slot.timezone}",
                    "pastTargetStrategy",
                    "fireImmediately")));
    VariableStore variables =
        new VariableStore(
            Map.of(
                "slot",
                Map.of(
                    "fixedTime", target.toLocalDateTime().toString(),
                    "timezone", "Asia/Shanghai")));

    long now = target.minusHours(1).toInstant().toEpochMilli();
    assertThat(TimerEvaluator.evaluate(node, variables, now).delayMs()).isEqualTo(3_600_000L);
  }

  @Test
  void firesImmediatelyWhenDateIsPast() {
    ExecutionNode node =
        timerNode(
            Map.of(
                "timerDefinition",
                Map.of(
                    "type",
                    "date",
                    "value",
                    "2020-01-01T00:00:00Z",
                    "pastTargetStrategy",
                    "fireImmediately")));
    long now = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();

    assertThat(TimerEvaluator.evaluate(node, new VariableStore(Map.of()), now).delayMs()).isZero();
  }

  @Test
  void failsWhenDateIsPastAndStrategyIsFail() {
    ExecutionNode node =
        timerNode(
            Map.of(
                "timerDefinition",
                Map.of(
                    "type",
                    "date",
                    "value",
                    "2020-01-01T00:00:00Z",
                    "pastTargetStrategy",
                    "fail")));
    long now = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();

    assertThatThrownBy(() -> TimerEvaluator.evaluate(node, new VariableStore(Map.of()), now))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Timer date is in the past");
  }

  @Test
  void resolvesTemplateExpressionInValue() {
    Object resolved = TimerEvaluator.resolveValue("${prefix}m", new VariableStore(Map.of("prefix", "3")));
    assertThat(resolved).isEqualTo("3m");
    assertThat(TimerEvaluator.parseDurationMs(String.valueOf(resolved))).isEqualTo(180_000L);
  }

  private static ExecutionNode timerNode(Map<String, Object> config) {
    return new ExecutionNode(
        "Wait",
        NodeKind.INTERMEDIATE_EVENT,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        config);
  }
}
