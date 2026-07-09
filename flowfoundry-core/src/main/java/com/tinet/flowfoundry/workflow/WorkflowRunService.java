package com.tinet.flowfoundry.workflow;

import com.tinet.flowfoundry.contract.FlowApiContracts.RunResponse;
import com.tinet.flowfoundry.contract.FlowApiContracts.StartSavedWorkflowRequest;
import com.tinet.flowfoundry.flow.FlowDefinition;
import com.tinet.flowfoundry.interpreter.runtime.RunSource;
import com.tinet.flowfoundry.run.FlowRunOrchestrator;
import com.tinet.flowfoundry.security.NamespaceAccessService;
import com.tinet.flowfoundry.workflow.dsl.CanvasToDslConverter;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Starts a Temporal run from a persisted workflow version (canvas model → DSL → start). */
@Service
public class WorkflowRunService {

  private final WorkflowDefinitionRepository definitionRepository;
  private final WorkflowVersionRepository versionRepository;
  private final NamespaceAccessService namespaceAccess;
  private final CanvasToDslConverter canvasToDslConverter;
  private final FlowRunOrchestrator flowRunOrchestrator;

  public WorkflowRunService(
      WorkflowDefinitionRepository definitionRepository,
      WorkflowVersionRepository versionRepository,
      NamespaceAccessService namespaceAccess,
      CanvasToDslConverter canvasToDslConverter,
      FlowRunOrchestrator flowRunOrchestrator) {
    this.definitionRepository = definitionRepository;
    this.versionRepository = versionRepository;
    this.namespaceAccess = namespaceAccess;
    this.canvasToDslConverter = canvasToDslConverter;
    this.flowRunOrchestrator = flowRunOrchestrator;
  }

  @Transactional(readOnly = true)
  public RunResponse startSavedVersion(
      String workflowId, String version, StartSavedWorkflowRequest request) {
    namespaceAccess.requireAuthenticatedNamespace();
    String namespace = namespaceAccess.resolveActiveNamespace();
    String normalizedVersion = VersionNumbering.ensureValid(version);

    WorkflowDefinitionEntity definition = requireDefinition(workflowId);
    if (!namespace.equals(definition.getNamespace())) {
      throw new WorkflowNotFoundException(workflowId);
    }
    requireActiveForExternalStart(definition);

    WorkflowVersionEntity versionEntity = requireVersion(workflowId, normalizedVersion);
    if (!WorkflowStatus.ACTIVE.value().equals(versionEntity.getStatus())) {
      throw new WorkflowConflictException(
          "Workflow version is not active and cannot be started via external API: "
              + workflowId
              + "@"
              + normalizedVersion
              + " (status="
              + versionEntity.getStatus()
              + ")");
    }

    Set<String> seen = new HashSet<>();
    seen.add(workflowId);
    FlowDefinition flow =
        canvasToDslConverter.convert(
            versionEntity.getModelJson(),
            normalizedVersion,
            seen,
            this::loadChildDsl);

    StartSavedWorkflowRequest body =
        request == null ? new StartSavedWorkflowRequest(null, null, null) : request;
    return flowRunOrchestrator.start(
        namespace,
        flow,
        body.input(),
        body.businessKey(),
        body.runWorkflowId(),
        RunSource.PRODUCTION);
  }

  private FlowDefinition loadChildDsl(
      String childWorkflowId, String preferredVersion, Set<String> seenWorkflowIds) {
    WorkflowDefinitionEntity child =
        definitionRepository.findById(childWorkflowId).orElse(null);
    if (child == null || !namespaceAccess.canAccess(child.getNamespace())) {
      return null;
    }
    child.getVersions().size();
    String versionToUse = resolveChildVersion(child, preferredVersion);
    if (versionToUse == null) {
      return null;
    }
    WorkflowVersionEntity versionEntity =
        child.getVersions().stream()
            .filter(v -> versionToUse.equals(v.getVersion()))
            .findFirst()
            .orElse(null);
    if (versionEntity == null || versionEntity.getModelJson() == null) {
      return null;
    }
    return canvasToDslConverter.convert(
        versionEntity.getModelJson(), versionToUse, seenWorkflowIds, this::loadChildDsl);
  }

  private static String resolveChildVersion(
      WorkflowDefinitionEntity child, String preferredVersion) {
    if (preferredVersion != null
        && !preferredVersion.isBlank()
        && !"latest".equalsIgnoreCase(preferredVersion)) {
      boolean exists =
          child.getVersions().stream().anyMatch(v -> preferredVersion.equals(v.getVersion()));
      if (exists) {
        return preferredVersion;
      }
    }
    return child.getCurrentVersion();
  }

  private void requireActiveForExternalStart(WorkflowDefinitionEntity definition) {
    if (!WorkflowStatus.ACTIVE.value().equals(definition.getStatus())) {
      throw new WorkflowConflictException(
          "Workflow is not active and cannot be started via external API: "
              + definition.getId()
              + " (status="
              + definition.getStatus()
              + ")");
    }
  }

  private WorkflowDefinitionEntity requireDefinition(String workflowId) {
    WorkflowDefinitionEntity definition =
        definitionRepository
            .findById(workflowId)
            .orElseThrow(() -> new WorkflowNotFoundException(workflowId));
    if (!namespaceAccess.canAccess(definition.getNamespace())) {
      throw new WorkflowNotFoundException(workflowId);
    }
    return definition;
  }

  private WorkflowVersionEntity requireVersion(String workflowId, String version) {
    return versionRepository
        .findById(new WorkflowVersionId(workflowId, version))
        .orElseThrow(() -> new WorkflowVersionNotFoundException(workflowId, version));
  }
}
