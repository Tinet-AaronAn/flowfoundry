package com.tinet.flowfoundry.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

public final class WorkflowContracts {

  private WorkflowContracts() {}

  public record WorkflowRecordDto(
      String id,
      String name,
      String version,
      String status,
      Instant updatedAt,
      List<WorkflowVersionDto> versions) {}

  public record WorkflowVersionDto(String version, String status, Instant createdAt, JsonNode model) {}

  public record CreateWorkflowRequest(String name, JsonNode model) {}

  public record SaveWorkflowVersionRequest(String name, JsonNode model, String status) {}

  public record CreateWorkflowVersionRequest(String sourceVersion, String version, JsonNode model) {}

  public record UpdateWorkflowRequest(String name, String status, String activeVersion) {}

  public record AllocateIdRequest(String kind) {}

  public record AllocateIdResponse(String kind, String id) {}
}
