package com.tinet.flowfoundry.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.tinet.flowfoundry.interpreter.model.InterpreterStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(FlowRunService.class)
class FlowRunServiceTest {

  @Autowired private FlowRunService flowRunService;

  @Test
  void registersRunAndRecordsEvents() {
    flowRunService.registerRun(
        new FlowRunContracts.FlowRunRegistration(
            "workflow_test_demo_001",
            "run-1",
            "default",
            "demo-flow",
            "Demo Flow",
            "1.0.0",
            "default:demo-flow-abc",
            "web-modeler",
            Map.of("campaignId", "c-1")));

    flowRunService.recordEvent(
        FlowRunEventCommand.event(
            "workflow_test_demo_001",
            "default",
            1,
            FlowRunEventType.WORKFLOW_STARTED,
            null,
            null,
            null,
            null,
            InterpreterStatus.RUNNING.name(),
            Map.of()));

    flowRunService.recordEvent(
        FlowRunEventCommand.event(
            "workflow_test_demo_001",
            "default",
            2,
            FlowRunEventType.NODE_ENTERED,
            "Task_A",
            "Task A",
            "ACTIVITY",
            "script-runtime",
            "RUNNING",
            Map.of()));

    flowRunService.recordEvent(
        new FlowRunEventCommand(
            "workflow_test_demo_001",
            "run-1",
            "default",
            3,
            FlowRunEventType.WORKFLOW_COMPLETED.name(),
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            InterpreterStatus.COMPLETED.name(),
            "demo-flow",
            "1.0.0",
            "default:demo-flow-abc",
            "web-modeler",
            "Demo Flow",
            null,
            null));

    FlowRunContracts.FlowRunListPage page = flowRunService.listRuns("default", "demo", 0, 10);
    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).workflowId()).isEqualTo("workflow_test_demo_001");
    assertThat(page.items().get(0).status()).isEqualTo(InterpreterStatus.COMPLETED.name());

    assertThat(flowRunService.listEvents("workflow_test_demo_001")).hasSize(3);
    assertThat(flowRunService.listNodeRuns("workflow_test_demo_001")).hasSize(1);
    assertThat(flowRunService.listNodeRuns("workflow_test_demo_001").get(0).nodeId())
        .isEqualTo("Task_A");
  }

  @Test
  void deduplicatesEventsBySequence() {
    flowRunService.registerRun(
        new FlowRunContracts.FlowRunRegistration(
            "workflow_test_demo_002",
            "run-2",
            "default",
            "demo-flow",
            "Demo Flow",
            "1.0.0",
            "default:demo-flow-def",
            "production",
            Map.of()));

    FlowRunEventCommand command =
        FlowRunEventCommand.event(
            "workflow_test_demo_002",
            "default",
            1,
            FlowRunEventType.NODE_ENTERED,
            "Task_B",
            "Task B",
            "ACTIVITY",
            "script-runtime",
            "RUNNING",
            Map.of());
    flowRunService.recordEvent(command);
    flowRunService.recordEvent(command);

    assertThat(flowRunService.listEvents("workflow_test_demo_002")).hasSize(1);
  }
}
