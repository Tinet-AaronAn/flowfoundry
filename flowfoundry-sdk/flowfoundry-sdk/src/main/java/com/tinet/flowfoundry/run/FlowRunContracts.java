package com.tinet.flowfoundry.run;

import java.time.Instant;
import java.util.List;

public final class FlowRunContracts {

  private FlowRunContracts() {}

  public record FlowRunListItem(
      String workflowId,
      String runId,
      String flowId,
      String flowName,
      String version,
      String namespace,
      String businessKey,
      String runSource,
      String status,
      String temporalStatus,
      Instant startedAt,
      Instant completedAt) {}

  public record FlowRunListPage(
      List<FlowRunListItem> items, int page, int size, long totalElements, int totalPages) {}

  public record FlowRunEventDto(
      long id,
      int sequenceNo,
      String eventType,
      String nodeId,
      String nodeName,
      String nodeKind,
      String activityType,
      String status,
      String detailJson,
      Instant occurredAt) {}

  public record FlowNodeRunDto(
      String nodeId,
      String nodeName,
      String nodeKind,
      String activityType,
      String status,
      Instant startedAt,
      Instant completedAt,
      String lastDetailJson) {}

  public record FlowRunRegistration(
      String workflowId,
      String temporalRunId,
      String namespace,
      String flowId,
      String flowName,
      String version,
      String businessKey,
      String runSource,
      Object input) {}
}
