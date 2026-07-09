package com.tinet.flowfoundry.temporal;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import com.tinet.flowfoundry.flow.FlowCompiler;
import com.tinet.flowfoundry.flow.FlowDefinition;
import com.tinet.flowfoundry.flow.TimerDefinitionRules;
import com.tinet.flowfoundry.interpreter.FlowInterpreterWorkflow;
import com.tinet.flowfoundry.interpreter.model.ExecutionNode;
import com.tinet.flowfoundry.interpreter.model.ExecutionPlan;
import com.tinet.flowfoundry.interpreter.model.NodeKind;
import com.tinet.flowfoundry.interpreter.runtime.RunSource;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleActionStartWorkflow;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleHandle;
import io.temporal.client.schedules.ScheduleOptions;
import io.temporal.client.schedules.ScheduleSpec;
import io.temporal.client.schedules.ScheduleUpdate;
import io.temporal.client.schedules.ScheduleUpdateInput;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnFlowFoundryPlatform
public class StartTimerScheduleService {

  private static final Logger log = LoggerFactory.getLogger(StartTimerScheduleService.class);

  private final FlowCompiler compiler;
  private final TemporalClients temporalClients;
  private final DeploymentContractRegistry contractRegistry;

  public StartTimerScheduleService(
      FlowCompiler compiler,
      TemporalClients temporalClients,
      DeploymentContractRegistry contractRegistry) {
    this.compiler = compiler;
    this.temporalClients = temporalClients;
    this.contractRegistry = contractRegistry;
  }

  public void syncFromDefinition(String workflowId, String namespace, FlowDefinition definition) {
    ExecutionPlan plan = compiler.compile(definition, namespace);
    ExecutionNode start = plan.startNode();
    if (!TimerDefinitionRules.isTimerStart(start.config())) {
      pauseSchedule(workflowId);
      return;
    }
    DeploymentContract contract = contractRegistry.resolveForNamespace(namespace);
    ScheduleClient scheduleClient = temporalClients.scheduleClient(namespace);
    ScheduleSpec spec = StartTimerScheduleMapper.toScheduleSpec(start, Instant.now());
    String businessKeyPrefix = namespace + ":" + plan.flowId();
    ScheduleActionStartWorkflow action =
        ScheduleActionStartWorkflow.newBuilder()
            .setWorkflowType(FlowInterpreterWorkflow.class)
            .setArguments(
                plan,
                businessKeyPrefix + "-scheduled-" + UUID.randomUUID(),
                Map.of(),
                RunSource.PRODUCTION.wireValue())
            .setOptions(
                WorkflowOptions.newBuilder().setTaskQueue(contract.taskQueue()).build())
            .build();
    Schedule schedule =
        Schedule.newBuilder().setAction(action).setSpec(spec).build();
    String scheduleId = scheduleId(workflowId);
    try {
      ScheduleHandle handle = scheduleClient.getHandle(scheduleId);
      handle.update(
          (ScheduleUpdateInput input) -> {
            Schedule.Builder builder = Schedule.newBuilder(input.getDescription().getSchedule());
            builder.setAction(action);
            builder.setSpec(spec);
            return new ScheduleUpdate(builder.build());
          });
      handle.unpause();
      log.info("Updated Timer Start schedule workflowId={} scheduleId={}", workflowId, scheduleId);
    } catch (Exception notFound) {
      scheduleClient.createSchedule(scheduleId, schedule, ScheduleOptions.newBuilder().build());
      log.info("Created Timer Start schedule workflowId={} scheduleId={}", workflowId, scheduleId);
    }
  }

  public void pauseSchedule(String workflowId) {
    String scheduleId = scheduleId(workflowId);
    try {
      businessScheduleClient().getHandle(scheduleId).pause("Workflow deactivated or not Timer Start");
      log.info("Paused Timer Start schedule workflowId={} scheduleId={}", workflowId, scheduleId);
    } catch (Exception ignored) {
      // No schedule registered.
    }
  }

  public void deleteSchedule(String workflowId) {
    String scheduleId = scheduleId(workflowId);
    try {
      businessScheduleClient().getHandle(scheduleId).delete();
      log.info("Deleted Timer Start schedule workflowId={} scheduleId={}", workflowId, scheduleId);
    } catch (Exception ignored) {
      // No schedule registered.
    }
  }

  private ScheduleClient businessScheduleClient() {
    return temporalClients.scheduleClient(contractRegistry.localContract().namespace());
  }

  static String scheduleId(String workflowId) {
    return "flowfoundry-timer-start-" + workflowId;
  }

  static boolean isTimerStartPlan(ExecutionPlan plan) {
    ExecutionNode start = plan.startNode();
    return start.requiredKind() == NodeKind.START
        && TimerDefinitionRules.isTimerStart(start.config());
  }
}
