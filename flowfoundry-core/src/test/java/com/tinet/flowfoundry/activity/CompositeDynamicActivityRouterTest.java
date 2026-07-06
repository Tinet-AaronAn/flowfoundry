package com.tinet.flowfoundry.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinet.flowfoundry.interpreter.runtime.ActivityExecutionContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompositeDynamicActivityRouterTest {

  @Test
  void routesCoreScriptRuntimeBeforeBusinessRouter() {
    ScriptRuntimeActivity scriptRuntime =
        new ScriptRuntimeActivity(new ObjectMapper(), new ScriptRuntimeProperties("", null, null, null));
    HumanTaskActivity humanTaskActivity = new HumanTaskActivity();
    CoreActivityRouter coreActivityRouter =
        new CoreActivityRouter(scriptRuntime, humanTaskActivity);
    BusinessActivityRouter businessRouter =
        new BusinessActivityRouter() {
          @Override
          public boolean supports(String activityType) {
            return "demo-action".equals(activityType);
          }

          @Override
          public Object execute(String activityType, Map<String, Object> input) {
            return Map.of("handledBy", "business");
          }
        };
    CompositeDynamicActivityRouter router =
        new CompositeDynamicActivityRouter(coreActivityRouter, List.of(businessRouter));

    Object scriptResult =
        router.execute(
            ActivityTypes.SCRIPT_RUNTIME,
            Map.of(
                "_config",
                Map.of("scriptCodeId", "demo", "scriptVersion", "1.0.0"),
                ActivityExecutionContext.CONTEXT_KEY,
                Map.of("runSource", "web-modeler")));
    Object businessResult = router.execute("demo-action", Map.of());

    assertThat(scriptResult).isInstanceOf(Map.class);
    assertThat(((Map<?, ?>) scriptResult).get("scriptCodeId")).isEqualTo("demo");
    assertThat(businessResult).isEqualTo(Map.of("handledBy", "business"));
  }

  private static CoreActivityRouter coreActivityRouter() {
    return new CoreActivityRouter(
        new ScriptRuntimeActivity(new ObjectMapper(), new ScriptRuntimeProperties("", null, null, null)),
        new HumanTaskActivity());
  }

  @Test
  void rejectsUnknownActivityType() {
    CompositeDynamicActivityRouter router =
        new CompositeDynamicActivityRouter(coreActivityRouter(), List.of());

    assertThatThrownBy(() -> router.execute("unknown-type", Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown-type");
  }
}
