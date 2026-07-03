package com.tinet.flowfoundary.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.tinet.flowfoundary.workflow.WorkflowContracts.AllocateIdResponse;
import com.tinet.flowfoundary.workflow.WorkflowContracts.CreateWorkflowRequest;
import com.tinet.flowfoundary.workflow.WorkflowContracts.CreateWorkflowVersionRequest;
import com.tinet.flowfoundary.workflow.WorkflowContracts.WorkflowRecordDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({
  JacksonAutoConfiguration.class,
  WorkflowService.class,
  WorkflowMapper.class,
  WorkflowModelFactory.class,
  PlatformIdGenerator.class,
  ShortIdGenerator.class
})
class WorkflowServiceTest {

  @Autowired private WorkflowService workflowService;

  @Test
  void createSaveAndVersionWorkflow() {
    WorkflowRecordDto created = workflowService.create(new CreateWorkflowRequest("Demo Flow", null));
    assertThat(created.id()).startsWith("workflow_");
    assertThat(created.version()).isEqualTo("1.0.0");
    assertThat(created.versions()).hasSize(1);

    WorkflowRecordDto versioned =
        workflowService.createVersion(created.id(), new CreateWorkflowVersionRequest("1.0.0", null, null));
    assertThat(versioned.version()).isEqualTo("1.0.1");
    assertThat(versioned.versions()).hasSize(2);
  }

  @Test
  void allocateTypedIds() {
    AllocateIdResponse task = workflowService.allocateId("task");
    AllocateIdResponse event = workflowService.allocateId("event");
    assertThat(task.id()).startsWith("task_");
    assertThat(event.id()).startsWith("event_");
    assertThat(task.id()).isNotEqualTo(event.id());
  }
}
