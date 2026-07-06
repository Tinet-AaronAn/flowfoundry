package com.tinet.flowfoundry.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.tinet.flowfoundry.interpreter.runtime.RunSource;
import com.tinet.flowfoundry.interpreter.runtime.RunSourceResolver;
import com.tinet.flowfoundry.workflow.WorkflowRunId;
import org.junit.jupiter.api.Test;

class FlowControllerRunSourceTest {

  @Test
  void runIdsDifferByRunSource() {
    String webId = WorkflowRunId.forRun(RunSource.WEB_MODELER, "demo");
    String prodId = WorkflowRunId.forRun(RunSource.PRODUCTION, "demo");
    assertThat(webId).startsWith("workflow_test_demo_");
    assertThat(prodId).startsWith("workflow_demo_");
    assertThat(webId).doesNotStartWith("workflow_test_test_");
    assertThat(RunSource.WEB_MODELER.usesStubActivities()).isTrue();
    assertThat(RunSource.PRODUCTION.usesStubActivities()).isFalse();
  }

  @Test
  void resolvesWebModelerOnlyForTrustedClient() {
    assertThat(RunSourceResolver.resolve("web-modeler", "web-modeler"))
        .isEqualTo(RunSource.WEB_MODELER);
    assertThat(RunSourceResolver.resolve("web-modeler", "external-client"))
        .isEqualTo(RunSource.PRODUCTION);
  }
}
