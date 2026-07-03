package com.tinet.flowfoundary.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.tinet.flowfoundary.interpreter.runtime.RunSource;
import com.tinet.flowfoundary.interpreter.runtime.RunSourceResolver;
import com.tinet.flowfoundary.workflow.WorkflowRunId;
import org.junit.jupiter.api.Test;

class FlowControllerRunSourceTest {

  @Test
  void runIdsAlwaysUseWorkflowPrefixRegardlessOfRunSource() {
    String webId = WorkflowRunId.forFlow("demo");
    String prodId = WorkflowRunId.forFlow("demo");
    assertThat(webId).startsWith("workflow_");
    assertThat(prodId).startsWith("workflow_");
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
