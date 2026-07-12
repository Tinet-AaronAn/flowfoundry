package com.tinet.flowfoundry.temporal;

import java.time.Instant;
import java.util.List;

public final class TemporalAdminContracts {

  private TemporalAdminContracts() {}

  public record TemporalClusterDto(
      String id,
      String displayName,
      String host,
      String uiBaseUrl,
      boolean defaultCluster,
      boolean reachable,
      String serverVersion,
      Instant createdAt,
      Instant updatedAt) {}

  public record CreateTemporalClusterRequest(
      String id, String displayName, String host, String uiBaseUrl, Boolean defaultCluster) {}

  public record UpdateTemporalClusterRequest(
      String displayName, String host, String uiBaseUrl, Boolean defaultCluster) {}

  public record TemporalOverviewDto(
      TemporalClusterSummaryDto cluster,
      TemporalSummaryCountsDto summary) {}

  public record TemporalClusterSummaryDto(
      String id,
      String displayName,
      String host,
      String uiBaseUrl,
      boolean reachable,
      String serverVersion,
      Instant checkedAt) {}

  public record TemporalSummaryCountsDto(
      int platformNamespaces,
      int temporalNamespaces,
      int ready,
      int noTemporalNs,
      int noWorker,
      int staleWorkers,
      int orphanTemporal) {}

  public record TemporalNamespaceAlignmentDto(
      String id,
      String displayName,
      String temporalClusterId,
      String temporalClusterName,
      boolean platformRegistered,
      boolean temporalRegistered,
      TemporalWorkerStatusDto worker,
      int taskQueuePollers,
      String status,
      String temporalUiUrl) {}

  public record TemporalWorkerStatusDto(
      String appId, String namespace, String taskQueue, boolean alive, Instant lastSeenAt) {}

  public record TemporalWorkerRowDto(
      String appId,
      String namespace,
      String taskQueue,
      String temporalClusterId,
      String temporalClusterName,
      String source,
      boolean alive,
      Instant lastSeenAt,
      int workflowPollers,
      int activityPollers,
      TemporalPlatformQueueDto platformCoreQueue) {}

  public record TemporalPlatformQueueDto(String queue, int pollers) {}

  public record TemporalScheduleDto(
      String scheduleId,
      String namespace,
      String temporalClusterId,
      String workflowId,
      String state,
      String note,
      Instant nextRunTime) {}

  public record TemporalNamespaceAlignmentPageDto(List<TemporalNamespaceAlignmentDto> items) {}

  public record TemporalWorkerPageDto(List<TemporalWorkerRowDto> items) {}

  public record TemporalSchedulePageDto(List<TemporalScheduleDto> items) {}

  public record TemporalClusterPageDto(List<TemporalClusterDto> items) {}
}
