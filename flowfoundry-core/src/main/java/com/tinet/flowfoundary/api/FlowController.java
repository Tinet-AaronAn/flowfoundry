package com.tinet.flowfoundary.api;

import com.tinet.flowfoundary.config.TemporalProperties;
import com.tinet.flowfoundary.flow.FlowCompiler;
import com.tinet.flowfoundary.flow.FlowDefinition;
import com.tinet.flowfoundary.interpreter.FlowInterpreterWorkflow;
import com.tinet.flowfoundary.interpreter.model.ExecutionPlan;
import com.tinet.flowfoundary.interpreter.model.HumanTaskCompletion;
import com.tinet.flowfoundary.api.RunStatusResponse;
import com.tinet.flowfoundary.interpreter.runtime.RunSource;
import com.tinet.flowfoundary.interpreter.runtime.RunSourceResolver;
import com.tinet.flowfoundary.registry.ActivityRegistry;
import com.tinet.flowfoundary.workflow.WorkflowRunId;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FlowController {

  private final FlowCompiler compiler;
  private final WorkflowClient workflowClient;
  private final TemporalProperties temporalProperties;
  private final ActivityRegistry activityRegistry;

  private final FlowRunStatusService runStatusService;

  public FlowController(
      FlowCompiler compiler,
      WorkflowClient workflowClient,
      TemporalProperties temporalProperties,
      ActivityRegistry activityRegistry,
      FlowRunStatusService runStatusService) {
    this.compiler = compiler;
    this.workflowClient = workflowClient;
    this.temporalProperties = temporalProperties;
    this.activityRegistry = activityRegistry;
    this.runStatusService = runStatusService;
  }

  @GetMapping("/activities")
  public ActivityRegistry activities() {
    return activityRegistry;
  }

  @PostMapping("/flows/compile")
  public ExecutionPlan compile(@RequestBody FlowDefinition definition) {
    return compiler.compile(definition);
  }

  @PostMapping("/flows/run")
  public RunResponse run(
      @RequestBody RunRequest request,
      @RequestHeader(value = RunSourceResolver.WEB_MODELER_CLIENT_HEADER, required = false)
          String clientHeader) {
    ExecutionPlan plan = compiler.compile(request.flow());
    RunSource runSource = RunSourceResolver.resolve(request.runSource(), clientHeader);
    String businessKey =
        request.businessKey() == null || request.businessKey().isBlank()
            ? plan.flowId() + "-" + UUID.randomUUID()
            : request.businessKey();
    String workflowId =
        request.workflowId() == null || request.workflowId().isBlank()
            ? WorkflowRunId.forFlow(plan.flowId())
            : requireRunWorkflowId(request.workflowId());

    FlowInterpreterWorkflow workflow =
        workflowClient.newWorkflowStub(
            FlowInterpreterWorkflow.class,
            WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(temporalProperties.taskQueue())
                .build());

    WorkflowExecution execution =
        WorkflowClient.start(
            workflow::run, plan, businessKey, safeMap(request.input()), runSource.wireValue());
    return new RunResponse(
        workflowId, execution.getRunId(), businessKey, runSource.wireValue(), plan);
  }

  private static String requireRunWorkflowId(String workflowId) {
    WorkflowRunId.requireTemporalRunId(workflowId);
    return workflowId;
  }

  @GetMapping("/flows/runs/{workflowId}")
  public RunStatusResponse state(@PathVariable String workflowId) {
    requireRunWorkflowId(workflowId);
    return runStatusService.getRunStatus(workflowId);
  }

  @PostMapping("/flows/runs/{workflowId}/human-task")
  public void completeHumanTask(
      @PathVariable String workflowId, @RequestBody HumanTaskCompletion completion) {
    requireRunWorkflowId(workflowId);
    workflowClient
        .newWorkflowStub(FlowInterpreterWorkflow.class, workflowId)
        .completeHumanTask(completion);
  }

  private static Map<String, Object> safeMap(Map<String, Object> input) {
    return input == null ? Map.of() : input;
  }

  public record RunRequest(
      FlowDefinition flow,
      String workflowId,
      String businessKey,
      String runSource,
      Map<String, Object> input) {}

  public record RunResponse(
      String workflowId,
      String runId,
      String businessKey,
      String runSource,
      ExecutionPlan executionPlan) {}
}
