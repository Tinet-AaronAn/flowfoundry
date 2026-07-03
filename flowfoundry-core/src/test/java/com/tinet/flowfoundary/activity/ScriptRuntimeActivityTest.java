package com.tinet.flowfoundary.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.tinet.flowfoundary.interpreter.runtime.ActivityExecutionContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScriptRuntimeActivityTest {

  @Test
  void returnsStubResultWhenServiceUrlIsNotConfigured() {
    ScriptRuntimeActivity activity = new ScriptRuntimeActivity(new ObjectMapper(), new ScriptRuntimeProperties(""));

    Map<String, Object> result =
        activity.execute(
            Map.of(
                "_config",
                Map.of("decisionRef", "risk-routing", "decisionVersion", "1.0.0"),
                ActivityExecutionContext.CONTEXT_KEY,
                Map.of("runSource", "production")));

    assertThat(result)
        .containsEntry("scriptRef", "risk-routing")
        .containsEntry("decisionRef", "risk-routing")
        .containsEntry("matched", true);
  }

  @Test
  void returnsStubResultForWebModelerRuns() {
    ScriptRuntimeActivity activity =
        new ScriptRuntimeActivity(new ObjectMapper(), new ScriptRuntimeProperties("http://127.0.0.1:9999/run"));

    Map<String, Object> result =
        activity.execute(
            Map.of(
                "_config",
                Map.of("decisionRef", "demo-script", "decisionVersion", "1.0.0"),
                ActivityExecutionContext.CONTEXT_KEY,
                webContext()));

    assertThat(result).containsEntry("matched", true).containsEntry("scriptRef", "demo-script");
  }

  private static Map<String, Object> webContext() {
    return Map.of("runSource", "web-modeler", "businessKey", "bk-1", "workflowId", "wf-1");
  }
}
