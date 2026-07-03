package com.tinet.flowfoundary.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.tinet.flowfoundary.interpreter.runtime.ActivityExecutionContext;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HumanTaskActivityTest {

  @Test
  void offlineModeDoesNotWait() {
    HumanTaskActivity activity = new HumanTaskActivity();

    Map<String, Object> result =
        activity.execute(
            Map.of(
                "_config",
                Map.of(
                    "nodeId",
                    "Task_Review",
                    "flowFoundryHumanTask",
                    Map.of("mode", "offline"))));

    assertThat(result)
        .containsEntry("mode", "offline")
        .containsEntry("outcome", "offline")
        .containsEntry("waiting", false);
  }

  @Test
  void managedModeRegistersTaskForWebModeler() {
    HumanTaskActivity activity = new HumanTaskActivity();

    Map<String, Object> result =
        activity.execute(
            Map.of(
                "_config",
                Map.of(
                    "nodeId",
                    "Task_Review",
                    "flowFoundryHumanTask",
                    Map.of("mode", "managed"),
                    "flowFoundryAssignmentDefinition",
                    Map.of("candidateGroups", "ops")),
                ActivityExecutionContext.CONTEXT_KEY,
                Map.of("runSource", "web-modeler")));

    assertThat(result)
        .containsEntry("mode", "managed")
        .containsEntry("waiting", true)
        .containsEntry("nodeId", "Task_Review");
    assertThat(result.get("taskId")).asString().startsWith("stub-human-task:");
  }
}
