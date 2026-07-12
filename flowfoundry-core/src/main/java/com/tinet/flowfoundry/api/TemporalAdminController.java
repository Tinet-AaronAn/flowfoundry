package com.tinet.flowfoundry.api;

import com.tinet.flowfoundry.security.AdminAccessService;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.TemporalClusterPageDto;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.TemporalClusterDto;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.TemporalNamespaceAlignmentDto;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.TemporalNamespaceAlignmentPageDto;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.TemporalOverviewDto;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.TemporalSchedulePageDto;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.TemporalWorkerPageDto;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.CreateTemporalClusterRequest;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.UpdateTemporalClusterRequest;
import com.tinet.flowfoundry.temporal.TemporalAdminService;
import com.tinet.flowfoundry.temporal.TemporalClusterAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/temporal")
public class TemporalAdminController {

  private final TemporalAdminService temporalAdminService;
  private final TemporalClusterAdminService clusterAdminService;
  private final AdminAccessService adminAccessService;

  public TemporalAdminController(
      TemporalAdminService temporalAdminService,
      TemporalClusterAdminService clusterAdminService,
      AdminAccessService adminAccessService) {
    this.temporalAdminService = temporalAdminService;
    this.clusterAdminService = clusterAdminService;
    this.adminAccessService = adminAccessService;
  }

  @GetMapping("/overview")
  public TemporalOverviewDto overview() {
    adminAccessService.requireAdmin();
    return temporalAdminService.overview();
  }

  @GetMapping("/namespaces")
  public TemporalNamespaceAlignmentPageDto namespaces(
      @RequestParam(defaultValue = "false") boolean refresh) {
    adminAccessService.requireAdmin();
    return temporalAdminService.namespaceAlignment(refresh);
  }

  @PostMapping("/namespaces/{namespaceId}/register")
  public TemporalNamespaceAlignmentDto registerNamespace(@PathVariable String namespaceId) {
    return temporalAdminService.registerTemporalNamespace(namespaceId);
  }

  @GetMapping("/workers")
  public TemporalWorkerPageDto workers(@RequestParam(defaultValue = "false") boolean refresh) {
    adminAccessService.requireAdmin();
    return temporalAdminService.workers(refresh);
  }

  @GetMapping("/schedules")
  public TemporalSchedulePageDto schedules(
      @RequestParam(required = false) String namespace) {
    adminAccessService.requireAdmin();
    return temporalAdminService.schedules(namespace);
  }

  @PostMapping("/schedules/{namespaceId}/{scheduleId}/pause")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void pauseSchedule(
      @PathVariable String namespaceId, @PathVariable String scheduleId) {
    temporalAdminService.pauseSchedule(namespaceId, scheduleId);
  }

  @PostMapping("/schedules/{namespaceId}/{scheduleId}/resume")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resumeSchedule(
      @PathVariable String namespaceId, @PathVariable String scheduleId) {
    temporalAdminService.resumeSchedule(namespaceId, scheduleId);
  }

  @GetMapping("/clusters")
  public TemporalClusterPageDto listClusters() {
    return clusterAdminService.list();
  }

  @GetMapping("/clusters/{clusterId}")
  public TemporalClusterDto getCluster(@PathVariable String clusterId) {
    return clusterAdminService.get(clusterId);
  }

  @PostMapping("/clusters")
  @ResponseStatus(HttpStatus.CREATED)
  public TemporalClusterDto createCluster(@RequestBody CreateTemporalClusterRequest request) {
    return clusterAdminService.create(request);
  }

  @PutMapping("/clusters/{clusterId}")
  public TemporalClusterDto updateCluster(
      @PathVariable String clusterId, @RequestBody UpdateTemporalClusterRequest request) {
    return clusterAdminService.update(clusterId, request);
  }

  @DeleteMapping("/clusters/{clusterId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteCluster(@PathVariable String clusterId) {
    clusterAdminService.delete(clusterId);
  }
}
