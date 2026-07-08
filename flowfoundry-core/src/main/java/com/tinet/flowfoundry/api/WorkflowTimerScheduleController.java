package com.tinet.flowfoundry.api;

import com.tinet.flowfoundry.flow.FlowDefinition;
import com.tinet.flowfoundry.security.NamespaceAccessService;
import com.tinet.flowfoundry.temporal.StartTimerScheduleService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflows/{workflowId}/timer-schedule")
public class WorkflowTimerScheduleController {

  private final StartTimerScheduleService scheduleService;
  private final NamespaceAccessService namespaceAccess;

  public WorkflowTimerScheduleController(
      StartTimerScheduleService scheduleService, NamespaceAccessService namespaceAccess) {
    this.scheduleService = scheduleService;
    this.namespaceAccess = namespaceAccess;
  }

  @PostMapping("/sync")
  public void sync(
      @PathVariable String workflowId, @RequestBody FlowDefinition definition) {
    namespaceAccess.requireAuthenticatedNamespace();
    String namespace = namespaceAccess.resolveActiveNamespace();
    scheduleService.syncFromDefinition(workflowId, namespace, definition);
  }

  @PostMapping("/pause")
  public void pause(@PathVariable String workflowId) {
    namespaceAccess.requireAuthenticatedNamespace();
    scheduleService.pauseSchedule(workflowId);
  }
}
