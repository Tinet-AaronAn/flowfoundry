package com.tinet.flowfoundry.interpreter.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskHeadersTest {

  @Test
  void readsTaskHeadersFromActivityInputConfig() {
    Map<String, Object> input =
        Map.of(
            "_config",
            Map.of(
                TaskHeaders.CONFIG_KEY,
                Map.of("x-region", "demo", "x-priority", "high")));

    assertThat(TaskHeaders.fromActivityInput(input))
        .containsEntry("x-region", "demo")
        .containsEntry("x-priority", "high");
  }

  @Test
  void returnsEmptyWhenTaskHeadersMissing() {
    assertThat(TaskHeaders.fromActivityInput(Map.of("_config", Map.of("decisionRef", "demo"))))
        .isEmpty();
    assertThat(TaskHeaders.fromConfig(null)).isEmpty();
  }
}
