package com.tinet.flowfoundry.temporal;

import static org.assertj.core.api.Assertions.assertThat;

import com.tinet.flowfoundry.interpreter.model.ExecutionNode;
import com.tinet.flowfoundry.interpreter.model.NodeKind;
import io.temporal.client.schedules.ScheduleSpec;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StartTimerScheduleMapperTest {

  @Test
  void mapsCycleToIntervalSpec() {
    ExecutionNode start =
        new ExecutionNode(
            "Start",
            NodeKind.START,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Map.of(
                "startEventSubtype",
                "timer",
                "timerDefinition",
                Map.of("type", "cycle", "value", "R/PT30M")));

    ScheduleSpec spec = StartTimerScheduleMapper.toScheduleSpec(start, Instant.parse("2026-07-07T00:00:00Z"));

    assertThat(spec.getIntervals()).hasSize(1);
    assertThat(spec.getIntervals().get(0).getEvery().toMinutes()).isEqualTo(30);
  }
}
