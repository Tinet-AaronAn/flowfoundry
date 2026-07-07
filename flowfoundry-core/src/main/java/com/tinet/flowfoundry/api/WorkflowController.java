package com.tinet.flowfoundry.api;

import com.tinet.flowfoundry.security.NamespaceAccessService;
import com.tinet.flowfoundry.workflow.TenantContextDto;
import com.tinet.flowfoundry.workflow.WorkflowContracts.AllocateIdRequest;
import com.tinet.flowfoundry.workflow.WorkflowContracts.AllocateIdResponse;
import com.tinet.flowfoundry.workflow.WorkflowContracts.CreateWorkflowRequest;
import com.tinet.flowfoundry.workflow.WorkflowContracts.CreateWorkflowVersionRequest;
import com.tinet.flowfoundry.workflow.WorkflowContracts.SaveWorkflowVersionRequest;
import com.tinet.flowfoundry.workflow.WorkflowContracts.UpdateWorkflowRequest;
import com.tinet.flowfoundry.workflow.WorkflowContracts.WorkflowRecordDto;
import com.tinet.flowfoundry.workflow.WorkflowService;
import com.tinet.flowfoundry.workflow.WorkflowContracts.WorkflowVersionDto;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

  private final WorkflowService workflowService;
  private final NamespaceAccessService namespaceAccess;

  public WorkflowController(
      WorkflowService workflowService, NamespaceAccessService namespaceAccess) {
    this.workflowService = workflowService;
    this.namespaceAccess = namespaceAccess;
  }

  @GetMapping("/context")
  public TenantContextDto tenantContext() {
    return namespaceAccess.tenantContext();
  }

  @GetMapping
  public List<WorkflowRecordDto> list(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status) {
    return workflowService.list(keyword, status);
  }

  @GetMapping("/{workflowId}")
  public WorkflowRecordDto get(@PathVariable String workflowId) {
    return workflowService.get(workflowId);
  }

  @GetMapping("/{workflowId}/versions/{version}")
  public WorkflowVersionDto getVersion(
      @PathVariable String workflowId, @PathVariable String version) {
    return workflowService.getVersion(workflowId, version);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public WorkflowRecordDto create(@RequestBody CreateWorkflowRequest request) {
    return workflowService.create(request);
  }

  @PutMapping("/{workflowId}/versions/{version}")
  public WorkflowRecordDto saveVersion(
      @PathVariable String workflowId,
      @PathVariable String version,
      @RequestBody SaveWorkflowVersionRequest request) {
    return workflowService.saveVersion(workflowId, version, request);
  }

  @PostMapping("/{workflowId}/versions")
  @ResponseStatus(HttpStatus.CREATED)
  public WorkflowRecordDto createVersion(
      @PathVariable String workflowId, @RequestBody CreateWorkflowVersionRequest request) {
    return workflowService.createVersion(workflowId, request);
  }

  @PatchMapping("/{workflowId}")
  public WorkflowRecordDto updateWorkflow(
      @PathVariable String workflowId, @RequestBody UpdateWorkflowRequest request) {
    return workflowService.updateWorkflow(workflowId, request);
  }

  @DeleteMapping("/{workflowId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String workflowId) {
    workflowService.delete(workflowId);
  }

  @PostMapping("/ids")
  public AllocateIdResponse allocateId(@RequestBody AllocateIdRequest request) {
    return workflowService.allocateId(request.kind());
  }

  @GetMapping("/ids/kinds")
  public Map<String, Object> idKinds() {
    return Map.of("kinds", List.of("workflow", "event", "subprocess", "task", "gateway", "participant"));
  }
}
