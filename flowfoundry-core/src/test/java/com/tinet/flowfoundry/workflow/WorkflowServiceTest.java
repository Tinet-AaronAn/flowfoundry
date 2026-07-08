package com.tinet.flowfoundry.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.tinet.flowfoundry.workflow.WorkflowContracts.AllocateIdResponse;
import com.tinet.flowfoundry.workflow.WorkflowContracts.CreateWorkflowRequest;
import com.tinet.flowfoundry.workflow.WorkflowContracts.CreateWorkflowVersionRequest;
import com.tinet.flowfoundry.workflow.WorkflowContracts.WorkflowRecordDto;
import com.tinet.flowfoundry.security.AdminAccessService;
import com.tinet.flowfoundry.security.ApiClientService;
import com.tinet.flowfoundry.security.AuditLogService;
import com.tinet.flowfoundry.security.NamespaceAccessService;
import com.tinet.flowfoundry.config.TemporalProperties;
import com.tinet.flowfoundry.flow.FlowCompiler;
import com.tinet.flowfoundry.security.PlatformSecurityProperties;
import com.tinet.flowfoundry.registry.ActivityRegistry;
import com.tinet.flowfoundry.temporal.StartTimerScheduleService;
import io.temporal.client.schedules.ScheduleClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "flowfoundry.security.enabled=false",
      "flowfoundry.security.dev-namespace=test-ns"
    })
@Import({
  JacksonAutoConfiguration.class,
  WorkflowService.class,
  WorkflowMapper.class,
  WorkflowModelFactory.class,
  PlatformIdGenerator.class,
  ShortIdGenerator.class,
  NamespaceAccessService.class,
  AdminAccessService.class,
  ApiClientService.class,
  AuditLogService.class,
  PlatformSecurityProperties.class,
  WorkflowServiceTest.ScheduleTestConfig.class
})
class WorkflowServiceTest {

  @Autowired private WorkflowService workflowService;

  @TestConfiguration
  static class ScheduleTestConfig {
    @Bean
    StartTimerScheduleService startTimerScheduleService() {
      return new StartTimerScheduleService(
          new FlowCompiler(new ActivityRegistry("1.0", "test", "test-queue", List.of())),
          Mockito.mock(ScheduleClient.class),
          new TemporalProperties("localhost:7233", "default", "test-queue", 10, 10, null));
    }
  }

  @Test
  void createSaveAndVersionWorkflow() {
    WorkflowRecordDto created = workflowService.create(new CreateWorkflowRequest("Demo Flow", null));
    assertThat(created.id()).startsWith("workflow_");
    assertThat(created.namespace()).isEqualTo("test-ns");
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
