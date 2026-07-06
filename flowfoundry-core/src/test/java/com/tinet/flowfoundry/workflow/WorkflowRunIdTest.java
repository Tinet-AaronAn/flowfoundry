package com.tinet.flowfoundry.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tinet.flowfoundry.interpreter.runtime.RunSource;
import org.junit.jupiter.api.Test;

class WorkflowRunIdTest {

  @Test
  void forProductionRunUsesWorkflowPrefix() {
    String id = WorkflowRunId.forProductionRun("RuntimeSmoke");
    assertThat(id).startsWith("workflow_RuntimeSmoke_");
    assertThat(id).doesNotStartWith("workflow_test_");
  }

  @Test
  void forWebModelerRunUsesWorkflowTestPrefix() {
    String id = WorkflowRunId.forWebModelerRun("RuntimeSmoke");
    assertThat(id).startsWith("workflow_test_RuntimeSmoke_");
  }

  @Test
  void forChildWorkflowUsesWorkflowChildPrefix() {
    String id = WorkflowRunId.forChildWorkflow("child-flow", "bk-1");
    assertThat(id).isEqualTo("workflow_child_child-flow_bk-1");
  }

  @Test
  void forChildWorkflowUsesTestPrefixInWebModeler() {
    String id =
        WorkflowRunId.forChildWorkflow(RunSource.WEB_MODELER, "child-flow", "bk-1");
    assertThat(id).isEqualTo("workflow_test_child_child-flow_bk-1");
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
