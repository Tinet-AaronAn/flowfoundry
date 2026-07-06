package com.tinet.flowfoundry.interpreter.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ActivityExecutionContextTest {

  @Test
  void parsesExecutionContextFromActivityInput() {
    ActivityExecutionContext context =
        ActivityExecutionContext.from(
            Map.of(
                ActivityExecutionContext.CONTEXT_KEY,
                Map.of(
                    "runSource", "web-modeler",
                    "businessKey", "bk-1",
                    "workflowId", "workflow_e2e_1")));

    assertThat(context.runSource()).isEqualTo(RunSource.WEB_MODELER);
    assertThat(context.businessKey()).isEqualTo("bk-1");
    assertThat(context.workflowId()).isEqualTo("workflow_e2e_1");
    assertThat(context.usesStubActivities()).isTrue();
  }

  @Test
  void defaultsToProductionWhenContextMissing() {
    ActivityExecutionContext context = ActivityExecutionContext.from(Map.of("campaignId", "demo"));

    assertThat(context.runSource()).isEqualTo(RunSource.PRODUCTION);
    assertThat(context.usesStubActivities()).isFalse();
  }
}
