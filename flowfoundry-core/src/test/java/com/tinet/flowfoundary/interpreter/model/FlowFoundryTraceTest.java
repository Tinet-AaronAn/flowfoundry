package com.tinet.flowfoundary.interpreter.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FlowFoundryTraceTest {

  @Test
  void buildsActivitySummaryFromTrace() {
    FlowFoundryTrace trace =
        new FlowFoundryTrace(
            "Task_ImportNumbers", "Import number batch", "serviceTask", "import-numbers");

    assertThat(trace.activitySummary())
        .isEqualTo("import-numbers · Import number batch (Task_ImportNumbers)");
  }

  @Test
  void truncatesSummaryToTwoHundredBytes() {
    String longName = "x".repeat(250);
    FlowFoundryTrace trace =
        new FlowFoundryTrace("Task_Long", longName, "serviceTask", "import-numbers");

    assertThat(trace.activitySummary().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
        .isLessThanOrEqualTo(200);
    assertThat(trace.activitySummary()).endsWith("...");
  }

  @Test
  void readsTraceFromActivityInput() {
    Map<String, Object> input =
        Map.of(
            FlowFoundryTrace.INPUT_KEY,
            Map.of(
                "nodeId", "Task_Review",
                "nodeName", "Owner approval",
                "canvasKind", "humanTask",
                "activityType", "human-task"));

    FlowFoundryTrace trace = FlowFoundryTrace.fromInput(input);

    assertThat(trace.nodeId()).isEqualTo("Task_Review");
    assertThat(trace.nodeName()).isEqualTo("Owner approval");
    assertThat(trace.canvasKind()).isEqualTo("humanTask");
  }
}
