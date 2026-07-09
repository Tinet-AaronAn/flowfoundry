package com.tinet.flowfoundry.run;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record FlowRunEventCommand(
    String workflowId,
    String temporalRunId,
    String namespace,
    int sequenceNo,
    String eventType,
    String nodeId,
    String nodeName,
    String nodeKind,
    String activityType,
    String status,
    String detailJson,
    long occurredAtEpochMs,
    String runStatus,
    String flowId,
    String flowVersion,
    String businessKey,
    String runSource,
    String flowName,
    String failureMessage,
    String failureType) {

  public static FlowRunEventCommand event(
      String workflowId,
      String namespace,
      int sequenceNo,
      FlowRunEventType eventType,
      String nodeId,
      String nodeName,
      String nodeKind,
      String activityType,
      String status,
      Map<String, Object> detail) {
    return new FlowRunEventCommand(
        workflowId,
        null,
        namespace,
        sequenceNo,
        eventType.name(),
        nodeId,
        nodeName,
        nodeKind,
        activityType,
        status,
        FlowRunJson.toJson(detail),
        Instant.now().toEpochMilli(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
