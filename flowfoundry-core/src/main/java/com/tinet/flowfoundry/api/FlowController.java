package com.tinet.flowfoundry.api;

import com.tinet.flowfoundry.flow.FlowCompiler;
import com.tinet.flowfoundry.contract.FlowApiContracts.RunRequest;
import com.tinet.flowfoundry.contract.FlowApiContracts.RunResponse;
import com.tinet.flowfoundry.contract.RunStatusResponse;
import com.tinet.flowfoundry.flow.FlowDefinition;
import com.tinet.flowfoundry.interpreter.FlowInterpreterWorkflow;
import com.tinet.flowfoundry.interpreter.model.ExecutionPlan;
import com.tinet.flowfoundry.interpreter.model.HumanTaskCompletion;
import com.tinet.flowfoundry.interpreter.runtime.RunSource;
import com.tinet.flowfoundry.interpreter.runtime.RunSourceResolver;
import com.tinet.flowfoundry.registry.ActivityCatalogService;
import com.tinet.flowfoundry.registry.ActivityRegistry;
import com.tinet.flowfoundry.run.FlowRunContracts;
import com.tinet.flowfoundry.run.FlowRunOrchestrator;
import com.tinet.flowfoundry.run.FlowRunService;
import com.tinet.flowfoundry.security.NamespaceAccessService;
import com.tinet.flowfoundry.temporal.RunNamespaceLocator;
import com.tinet.flowfoundry.temporal.TemporalClients;
import com.tinet.flowfoundry.workflow.WorkflowRunId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FlowController {

  private final FlowCompiler compiler;
  private final TemporalClients temporalClients;
  private final RunNamespaceLocator runNamespaceLocator;
  private final ActivityCatalogService activityCatalog;
  private final FlowRunStatusService runStatusService;
  private final FlowRunService flowRunService;
  private final FlowRunOrchestrator flowRunOrchestrator;
  private final NamespaceAccessService namespaceAccess;

  public FlowController(
      FlowCompiler compiler,
      TemporalClients temporalClients,
      RunNamespaceLocator runNamespaceLocator,
      ActivityCatalogService activityCatalog,
      FlowRunStatusService runStatusService,
      FlowRunService flowRunService,
      FlowRunOrchestrator flowRunOrchestrator,
      NamespaceAccessService namespaceAccess) {
    this.compiler = compiler;
    this.temporalClients = temporalClients;
    this.runNamespaceLocator = runNamespaceLocator;
    this.activityCatalog = activityCatalog;
    this.runStatusService = runStatusService;
    this.flowRunService = flowRunService;
    this.flowRunOrchestrator = flowRunOrchestrator;
    this.namespaceAccess = namespaceAccess;
  }

  @GetMapping("/activities")
  public ActivityRegistry activities() {
    String namespace = namespaceAccess.resolveActiveNamespace();
    namespaceAccess.requireAccess(namespace);
    return activityCatalog.forNamespace(namespace);
  }

  @PostMapping("/flows/compile")
  public ExecutionPlan compile(@RequestBody FlowDefinition definition) {
    namespaceAccess.requireAuthenticatedNamespace();
    String namespace = namespaceAccess.resolveActiveNamespace();
    return compiler.compile(definition, namespace);
  }

  @PostMapping("/flows/run")
  public RunResponse run(
      @RequestBody RunRequest request,
      @RequestHeader(value = RunSourceResolver.WEB_MODELER_CLIENT_HEADER, required = false)
          String clientHeader) {
    namespaceAccess.requireAuthenticatedNamespace();
    String namespace = namespaceAccess.resolveActiveNamespace();
    RunSource runSource = RunSourceResolver.resolve(request.runSource(), clientHeader);
    return flowRunOrchestrator.start(
        namespace,
        request.flow(),
        request.input(),
        request.businessKey(),
        request.workflowId(),
        runSource);
  }

  private static String requireRunWorkflowId(String workflowId) {
    WorkflowRunId.requireTemporalRunId(workflowId);
    return workflowId;
  }

  @GetMapping("/flows/runs")
  public FlowRunContracts.FlowRunListPage listRuns(
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    namespaceAccess.requireAuthenticatedNamespace();
    String namespace = namespaceAccess.resolveActiveNamespace();
    return flowRunService.listRuns(namespace, keyword, page, size);
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
}
