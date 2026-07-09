package com.tinet.flowfoundry.run;

import com.tinet.flowfoundry.contract.FlowApiContracts.RunResponse;
import com.tinet.flowfoundry.flow.FlowCompiler;
import com.tinet.flowfoundry.flow.FlowDefinition;
import com.tinet.flowfoundry.interpreter.FlowInterpreterWorkflow;
import com.tinet.flowfoundry.interpreter.model.ExecutionPlan;
import com.tinet.flowfoundry.interpreter.runtime.RunSource;
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
import org.springframework.stereotype.Service;

/** Shared Temporal start + run registration used by FlowController and saved-version start API. */
@Service
public class FlowRunOrchestrator {

  private final FlowCompiler compiler;
  private final TemporalClients temporalClients;
  private final DeploymentContractRegistry contractRegistry;
  private final RunNamespaceLocator runNamespaceLocator;
  private final FlowRunService flowRunService;

  public FlowRunOrchestrator(
      FlowCompiler compiler,
      TemporalClients temporalClients,
      DeploymentContractRegistry contractRegistry,
      RunNamespaceLocator runNamespaceLocator,
      FlowRunService flowRunService) {
    this.compiler = compiler;
    this.temporalClients = temporalClients;
    this.contractRegistry = contractRegistry;
    this.runNamespaceLocator = runNamespaceLocator;
    this.flowRunService = flowRunService;
  }

  public RunResponse start(
      String namespace,
      FlowDefinition flow,
      Map<String, Object> input,
      String businessKey,
      String runWorkflowId,
      RunSource runSource) {
    ExecutionPlan plan = compiler.compile(flow, namespace);
    String resolvedBusinessKey =
        businessKey == null || businessKey.isBlank()
            ? namespace + ":" + plan.flowId() + "-" + UUID.randomUUID()
            : namespace + ":" + businessKey;
    String workflowId =
        runWorkflowId == null || runWorkflowId.isBlank()
            ? WorkflowRunId.forRun(runSource, plan.flowId())
            : requireRunWorkflowId(runWorkflowId);

    DeploymentContract contract = contractRegistry.resolveForNamespace(namespace);
    WorkflowClient workflowClient = temporalClients.workflowClient(namespace);
    FlowInterpreterWorkflow workflow =
        workflowClient.newWorkflowStub(
            FlowInterpreterWorkflow.class,
            WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(contract.taskQueue())
                .build());

    Map<String, Object> safeInput = safeMap(input);
    WorkflowExecution execution =
        WorkflowClient.start(
            workflow::run, plan, resolvedBusinessKey, safeInput, runSource.wireValue());
    runNamespaceLocator.remember(workflowId, namespace);
    String flowName =
        flow != null && flow.flow() != null ? flow.flow().name() : plan.flowId();
    flowRunService.registerRun(
        new FlowRunContracts.FlowRunRegistration(
            workflowId,
            execution.getRunId(),
            namespace,
            plan.flowId(),
            flowName,
            plan.version(),
            resolvedBusinessKey,
            runSource.wireValue(),
            safeInput));
    return new RunResponse(
        workflowId, execution.getRunId(), resolvedBusinessKey, runSource.wireValue(), plan);
  }

  private static String requireRunWorkflowId(String workflowId) {
    WorkflowRunId.requireTemporalRunId(workflowId);
    return workflowId;
  }

  private static Map<String, Object> safeMap(Map<String, Object> input) {
    return input == null ? Map.of() : input;
  }
}
