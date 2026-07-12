package com.tinet.flowfoundry.plugin;

import java.time.Instant;
import java.util.List;

public final class PluginContracts {

  private PluginContracts() {}

  public record PluginDto(
      String id,
      String version,
      String displayName,
      String description,
      String namespace,
      String taskQueue,
      boolean typedWorkflows,
      String state,
      String desiredState,
      int replicas,
      String jarSha256,
      String errorDetail,
      String uploadedBy,
      Integer readyReplicas,
      Integer runtimeDesiredReplicas,
      Boolean runtimeHealthy,
      Integer activityPollers,
      String runtimeSummary,
      Instant createdAt,
      Instant updatedAt) {}

  public record PluginPageDto(List<PluginDto> items) {}

  public record ScalePluginRequest(Integer replicas) {}
}
