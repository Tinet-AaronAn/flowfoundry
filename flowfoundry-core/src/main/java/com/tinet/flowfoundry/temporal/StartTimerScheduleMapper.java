package com.tinet.flowfoundry.temporal;

import com.tinet.flowfoundry.flow.CycleTimerExpression;
import com.tinet.flowfoundry.flow.TimerDefinitionRules;
import com.tinet.flowfoundry.interpreter.model.ExecutionNode;
import com.tinet.flowfoundry.interpreter.runtime.TimerEvaluator;
import io.temporal.client.schedules.ScheduleIntervalSpec;
import io.temporal.client.schedules.ScheduleSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Maps Timer Start Event config to Temporal {@link ScheduleSpec}. */
final class StartTimerScheduleMapper {

  private StartTimerScheduleMapper() {}

  static ScheduleSpec toScheduleSpec(ExecutionNode startNode, Instant now) {
    Map<String, Object> config = startNode.config();
    if (!TimerDefinitionRules.isTimerStart(config)) {
      throw new IllegalArgumentException(
          "Start node is not configured as Timer Start: " + startNode.id());
    }
    Map<String, Object> timerDefinition = TimerDefinitionRules.timerDefinition(config);
    String type = TimerDefinitionRules.timerType(config);
    String value = String.valueOf(timerDefinition.get("value")).trim();
    CycleTimerExpression.rejectVariables(value, startNode.id());
    String timezone =
        timerDefinition.get("timezone") == null
            ? null
            : String.valueOf(timerDefinition.get("timezone"));
    if (timezone != null) {
      CycleTimerExpression.rejectVariables(timezone, startNode.id());
    }

    return switch (type) {
      case "cycle" -> buildCycleSpec(value, timezone, startNode.id());
      case "date" -> buildDateSpec(value, timezone, startNode.id(), now);
      default ->
          throw new IllegalArgumentException(
              "Timer Start schedule supports cycle or date only: " + startNode.id());
    };
  }

  private static ScheduleSpec buildCycleSpec(String value, String timezone, String nodeId) {
    CycleTimerExpression.Parsed parsed = CycleTimerExpression.parse(value, nodeId);
    Duration every = Duration.ofMillis(parsed.intervalMs());
    Instant anchor = CycleTimerExpression.parseStartInstant(parsed.startText(), timezone, nodeId);
    ScheduleIntervalSpec interval;
    if (anchor != null) {
      long phaseMs = Math.floorMod(anchor.toEpochMilli(), parsed.intervalMs());
      interval = new ScheduleIntervalSpec(every, Duration.ofMillis(phaseMs));
    } else {
      interval = new ScheduleIntervalSpec(every);
    }
    ScheduleSpec.Builder spec = ScheduleSpec.newBuilder().setIntervals(List.of(interval));
    if (anchor != null && anchor.isAfter(Instant.now())) {
      spec.setStartAt(anchor);
    }
    return spec.build();
  }

  private static ScheduleSpec buildDateSpec(
      String value, String timezone, String nodeId, Instant now) {
    long targetEpochMs =
        TimerEvaluator.parseTargetEpochMsForSchedule(value, timezone, nodeId);
    Instant target = Instant.ofEpochMilli(targetEpochMs);
    if (!target.isAfter(now)) {
      throw new IllegalArgumentException(
          "Timer Start date must be in the future to create a schedule: " + nodeId);
    }
    return ScheduleSpec.newBuilder().setStartAt(target).build();
  }
}
