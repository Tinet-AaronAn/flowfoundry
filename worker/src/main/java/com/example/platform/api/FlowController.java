package com.example.platform.api;

import com.example.platform.config.TemporalProperties;
import com.example.platform.flow.FlowCompiler;
import com.example.platform.flow.FlowDefinition;
import com.example.platform.interpreter.FlowInterpreterWorkflow;
import com.example.platform.interpreter.model.ExecutionPlan;
import com.example.platform.interpreter.model.HumanTaskCompletion;
import com.example.platform.interpreter.model.InterpreterState;
import com.example.platform.registry.ActivityRegistry;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FlowController {

  private final FlowCompiler compiler;
  private final WorkflowClient workflowClient;
  private final TemporalProperties temporalProperties;
  private final ActivityRegistry activityRegistry;

  public FlowController(
      FlowCompiler compiler,
      WorkflowClient workflowClient,
      TemporalProperties temporalProperties,
      ActivityRegistry activityRegistry) {
    this.compiler = compiler;
    this.workflowClient = workflowClient;
    this.temporalProperties = temporalProperties;
    this.activityRegistry = activityRegistry;
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
  public RunResponse run(@RequestBody RunRequest request) {
    ExecutionPlan plan = compiler.compile(request.flow());
    String businessKey =
        request.businessKey() == null || request.businessKey().isBlank()
            ? plan.flowId() + "-" + UUID.randomUUID()
            : request.businessKey();
    String workflowId =
        request.workflowId() == null || request.workflowId().isBlank()
            ? "flow-" + plan.flowId() + "-" + UUID.randomUUID()
            : request.workflowId();

    FlowInterpreterWorkflow workflow =
        workflowClient.newWorkflowStub(
            FlowInterpreterWorkflow.class,
            WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(temporalProperties.taskQueue())
                .build());

    WorkflowExecution execution =
        WorkflowClient.start(workflow::run, plan, businessKey, safeMap(request.input()));
    return new RunResponse(workflowId, execution.getRunId(), businessKey, plan);
  }

  @GetMapping("/flows/runs/{workflowId}")
  public InterpreterState state(@PathVariable String workflowId) {
    return workflowClient.newWorkflowStub(FlowInterpreterWorkflow.class, workflowId).getState();
  }

  @PostMapping("/flows/runs/{workflowId}/human-task")
  public void completeHumanTask(
      @PathVariable String workflowId, @RequestBody HumanTaskCompletion completion) {
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
      Map<String, Object> input) {}

  public record RunResponse(
      String workflowId, String runId, String businessKey, ExecutionPlan executionPlan) {}
}
