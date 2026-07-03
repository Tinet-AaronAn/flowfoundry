package com.tinet.flowfoundary.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WorkflowRunIdTest {

  @Test
  void forFlowUsesWorkflowPrefix() {
    String id = WorkflowRunId.forFlow("RuntimeSmoke");
    assertThat(id).startsWith("workflow_RuntimeSmoke_");
  }

  @Test
  void forChildWorkflowUsesWorkflowChildPrefix() {
    String id = WorkflowRunId.forChildWorkflow("child-flow", "bk-1");
    assertThat(id).isEqualTo("workflow_child_child-flow_bk-1");
  }

  @Test
  void requireTemporalRunIdRejectsNonWorkflowPrefix() {
    assertThatThrownBy(() -> WorkflowRunId.requireTemporalRunId("flow-abc"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("workflow_");
  }

  @Test
  void isDefinitionIdMatchesEightCharSuffix() {
    assertThat(WorkflowRunId.isDefinitionId("workflow_abc12345")).isTrue();
    assertThat(WorkflowRunId.isDefinitionId("workflow_RuntimeSmoke_uuid")).isFalse();
  }
}
