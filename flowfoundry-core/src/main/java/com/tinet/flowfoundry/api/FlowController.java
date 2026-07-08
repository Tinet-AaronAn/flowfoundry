package com.tinet.flowfoundry.api;

import com.tinet.flowfoundry.flow.FlowCompiler;
import com.tinet.flowfoundry.flow.FlowDefinition;
import com.tinet.flowfoundry.interpreter.FlowInterpreterWorkflow;
import com.tinet.flowfoundry.interpreter.model.ExecutionPlan;
import com.tinet.flowfoundry.interpreter.model.HumanTaskCompletion;
import com.tinet.flowfoundry.api.RunStatusResponse;
import com.tinet.flowfoundry.interpreter.runtime.RunSource;
import com.tinet.flowfoundry.interpreter.runtime.RunSourceResolver;
import com.tinet.flowfoundry.registry.ActivityRegistry;
import com.tinet.flowfoundry.security.NamespaceAccessService;
import com.tinet.flowfoundry.temporal.DeploymentContract;
import com.tinet.flowfoundry.temporal.DeploymentContractRegistry;
import com.tinet.flowfoundry.temporal.RunNamespaceLocator;
import com.tinet.flowfoundry.temporal.TemporalClients;
import com.tinet.flowfoundry.workflow.WorkflowRunId;
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
  private final TemporalClients temporalClients;
  private final DeploymentContractRegistry contractRegistry;
  private final RunNamespaceLocator runNamespaceLocator;
  private final ActivityRegistry activityRegistry;

  private final FlowRunStatusService runStatusService;
  private final NamespaceAccessService namespaceAccess;

  public FlowController(
      FlowCompiler compiler,
      TemporalClients temporalClients,
      DeploymentContractRegistry contractRegistry,
      RunNamespaceLocator runNamespaceLocator,
      ActivityRegistry activityRegistry,
      FlowRunStatusService runStatusService,
      NamespaceAccessService namespaceAccess) {
    this.compiler = compiler;
    this.temporalClients = temporalClients;
    this.contractRegistry = contractRegistry;
    this.runNamespaceLocator = runNamespaceLocator;
    this.activityRegistry = activityRegistry;
    this.runStatusService = runStatusService;
    this.namespaceAccess = namespaceAccess;
  }

  @GetMapping("/activities")
  public ActivityRegistry activities() {
    namespaceAccess.requireAccess(activityRegistry.namespace());
    return activityRegistry;
  }

  @PostMapping("/flows/compile")
  public ExecutionPlan compile(@RequestBody FlowDefinition definition) {
    namespaceAccess.requireAuthenticatedNamespace();
    return compiler.compile(definition);
  }

  @PostMapping("/flows/run")
  public RunResponse run(
      @RequestBody RunRequest request,
      @RequestHeader(value = RunSourceResolver.WEB_MODELER_CLIENT_HEADER, required = false)
          String clientHeader) {
    namespaceAccess.requireAuthenticatedNamespace();
    String namespace = namespaceAccess.resolveActiveNamespace();
    ExecutionPlan plan = compiler.compile(request.flow());
    RunSource runSource = RunSourceResolver.resolve(request.runSource(), clientHeader);
    String businessKey =
        request.businessKey() == null || request.businessKey().isBlank()
            ? namespace + ":" + plan.flowId() + "-" + UUID.randomUUID()
            : namespace + ":" + request.businessKey();
    String workflowId =
        request.workflowId() == null || request.workflowId().isBlank()
            ? WorkflowRunId.forRun(runSource, plan.flowId())
            : requireRunWorkflowId(request.workflowId());

    DeploymentContract contract = contractRegistry.resolveForRun();
    // 后台建模器发起的调试运行（web-modeler）落到预留的系统 namespace，与业务生产 run logs 隔离；
    // 生产运行落到使用方业务 namespace。两者共用同一业务 Task Queue，由使用方 Worker 同时轮询。
    // 注意：这里的 temporalNamespace 是 Temporal 物理隔离单位，与上面的逻辑 namespace（RBAC / workflow 归属）无关。
    String temporalNamespace =
        runSource.usesStubActivities()
            ? contractRegistry.systemNamespace()
            : contract.temporalNamespace();
    WorkflowClient workflowClient = temporalClients.workflowClient(temporalNamespace);
    FlowInterpreterWorkflow workflow =
        workflowClient.newWorkflowStub(
            FlowInterpreterWorkflow.class,
            WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(contract.taskQueue())
                .build());

    WorkflowExecution execution =
        WorkflowClient.start(
            workflow::run, plan, businessKey, safeMap(request.input()), runSource.wireValue());
    runNamespaceLocator.remember(workflowId, temporalNamespace);
    return new RunResponse(
        workflowId, execution.getRunId(), businessKey, runSource.wireValue(), plan);
  }

  private static String requireRunWorkflowId(String workflowId) {
    WorkflowRunId.requireTemporalRunId(workflowId);
    return workflowId;
  }

  @GetMapping("/flows/runs/{workflowId}")
  public RunStatusResponse state(@PathVariable String workflowId) {
    namespaceAccess.requireAuthenticatedNamespace();
    requireRunWorkflowId(workflowId);
    return runStatusService.getRunStatus(workflowId);
  }

  @PostMapping("/flows/runs/{workflowId}/human-task")
  public void completeHumanTask(
      @PathVariable String workflowId, @RequestBody HumanTaskCompletion completion) {
    namespaceAccess.requireAuthenticatedNamespace();
    requireRunWorkflowId(workflowId);
    String namespace = runNamespaceLocator.locate(workflowId);
    temporalClients
        .workflowClient(namespace)
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
