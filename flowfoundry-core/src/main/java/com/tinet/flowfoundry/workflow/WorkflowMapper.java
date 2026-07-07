package com.tinet.flowfoundry.workflow;

import com.tinet.flowfoundry.workflow.WorkflowContracts.WorkflowRecordDto;
import com.tinet.flowfoundry.workflow.WorkflowContracts.WorkflowVersionDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WorkflowMapper {

  private final ObjectMapper objectMapper;

  public WorkflowMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public WorkflowRecordDto toRecord(WorkflowDefinitionEntity entity) {
    String currentVersion =
        entity.getCurrentVersion() == null ? VersionNumbering.INITIAL_VERSION : entity.getCurrentVersion();
    List<WorkflowVersionDto> versions =
        entity.getVersions().stream()
            .sorted(Comparator.comparing(WorkflowVersionEntity::getCreatedAt))
            .map(this::toVersionDto)
            .toList();
    return new WorkflowRecordDto(
        entity.getId(),
        entity.getName(),
        entity.getNamespace(),
        currentVersion,
        entity.getStatus(),
        entity.getUpdatedAt(),
        versions);
  }

  public WorkflowVersionDto toVersionDto(WorkflowVersionEntity entity) {
    return new WorkflowVersionDto(
        entity.getVersion(), entity.getStatus(), entity.getCreatedAt(), entity.getModelJson());
  }

  public JsonNode cloneModel(JsonNode model) {
    return objectMapper.valueToTree(model);
  }
}
