package com.tinet.flowfoundry.workflow;

import com.tinet.flowfoundry.security.NamespaceAccessService;
import com.tinet.flowfoundry.workflow.WorkflowContracts.AllocateIdResponse;
import com.tinet.flowfoundry.workflow.WorkflowContracts.CreateWorkflowRequest;
import com.tinet.flowfoundry.workflow.WorkflowContracts.CreateWorkflowVersionRequest;
import com.tinet.flowfoundry.workflow.WorkflowContracts.SaveWorkflowVersionRequest;
import com.tinet.flowfoundry.workflow.WorkflowContracts.UpdateWorkflowRequest;
import com.tinet.flowfoundry.workflow.WorkflowContracts.WorkflowRecordDto;
import com.tinet.flowfoundry.workflow.WorkflowContracts.WorkflowVersionDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowService {

  private final WorkflowDefinitionRepository definitionRepository;
  private final WorkflowVersionRepository versionRepository;
  private final PlatformIdGenerator idGenerator;
  private final WorkflowModelFactory modelFactory;
  private final WorkflowMapper mapper;
  private final NamespaceAccessService namespaceAccess;

  public WorkflowService(
      WorkflowDefinitionRepository definitionRepository,
      WorkflowVersionRepository versionRepository,
      PlatformIdGenerator idGenerator,
      WorkflowModelFactory modelFactory,
      WorkflowMapper mapper,
      NamespaceAccessService namespaceAccess) {
    this.definitionRepository = definitionRepository;
    this.versionRepository = versionRepository;
    this.idGenerator = idGenerator;
    this.modelFactory = modelFactory;
    this.mapper = mapper;
    this.namespaceAccess = namespaceAccess;
  }

  @Transactional(readOnly = true)
  public List<WorkflowRecordDto> list(String keyword, String status) {
    String normalizedStatus = status == null || status.isBlank() ? "" : WorkflowStatus.fromValue(status).value();
    String normalizedKeyword = normalizeKeyword(keyword);
    List<WorkflowDefinitionEntity> definitions;
    if (namespaceAccess.isAdmin() && namespaceAccess.allowedNamespaces().isEmpty()) {
      definitions = definitionRepository.searchAll(normalizedKeyword, normalizedStatus);
    } else {
      List<String> namespaces = namespaceAccess.allowedNamespaces().stream().sorted().toList();
      definitions = definitionRepository.search(normalizedKeyword, normalizedStatus, namespaces);
    }
    return definitions.stream()
        .map(this::loadWithVersions)
        .map(mapper::toRecord)
        .toList();
  }

  @Transactional(readOnly = true)
  public WorkflowRecordDto get(String workflowId) {
    return mapper.toRecord(loadWithVersions(requireDefinition(workflowId)));
  }

  @Transactional(readOnly = true)
  public WorkflowVersionDto getVersion(String workflowId, String version) {
    return mapper.toVersionDto(requireVersion(workflowId, VersionNumbering.ensureValid(version)));
  }

  @Transactional
  public WorkflowRecordDto create(CreateWorkflowRequest request) {
    String name = requireName(request.name());
    String namespace = namespaceAccess.resolveActiveNamespace();
    String workflowId = idGenerator.allocateWorkflowId();
    String version = VersionNumbering.INITIAL_VERSION;
    Instant now = Instant.now();
    JsonNode model =
        request.model() == null || request.model().isNull()
            ? modelFactory.emptyModel(workflowId, name)
            : normalizeModel(request.model(), workflowId, name);

    WorkflowDefinitionEntity definition = new WorkflowDefinitionEntity();
    definition.setId(workflowId);
    definition.setName(name);
    definition.setNamespace(namespace);
    definition.setStatus(WorkflowStatus.DRAFT.value());
    definition.setCurrentVersion(version);
    definition.setCreatedAt(now);
    definition.setUpdatedAt(now);

    WorkflowVersionEntity versionEntity = newVersionEntity(workflowId, version, WorkflowStatus.DRAFT, model, now);
    definition.getVersions().add(versionEntity);
    return mapper.toRecord(definitionRepository.save(definition));
  }

  @Transactional
  public WorkflowRecordDto saveVersion(String workflowId, String version, SaveWorkflowVersionRequest request) {
    WorkflowDefinitionEntity definition = loadWithVersions(requireDefinition(workflowId));
    String normalizedVersion = VersionNumbering.ensureValid(version);
    WorkflowVersionEntity versionEntity = requireVersionEntity(definition, normalizedVersion);
    Instant now = Instant.now();

    if (request.name() != null && !request.name().isBlank()) {
      definition.setName(request.name().trim());
      updateModelName(versionEntity.getModelJson(), definition.getId(), definition.getName());
    }
    if (request.model() != null && !request.model().isNull()) {
      JsonNode model = normalizeModel(request.model(), definition.getId(), definition.getName());
      versionEntity.setModelJson(model);
    }
    if (request.status() != null && !request.status().isBlank()) {
      String status = WorkflowStatus.fromValue(request.status()).value();
      definition.setStatus(status);
      versionEntity.setStatus(status);
    }
    versionEntity.setUpdatedAt(now);
    definition.setUpdatedAt(now);
    definition.setCurrentVersion(normalizedVersion);
    return mapper.toRecord(definitionRepository.save(definition));
  }

  @Transactional
  public WorkflowRecordDto createVersion(String workflowId, CreateWorkflowVersionRequest request) {
    WorkflowDefinitionEntity definition = loadWithVersions(requireDefinition(workflowId));
    String sourceVersion =
        request.sourceVersion() == null || request.sourceVersion().isBlank()
            ? definition.getCurrentVersion()
            : VersionNumbering.ensureValid(request.sourceVersion());
    WorkflowVersionEntity source = requireVersionEntity(definition, sourceVersion);
    String latestVersion =
        definition.getVersions().stream()
            .map(WorkflowVersionEntity::getVersion)
            .max(this::compareVersions)
            .orElse(VersionNumbering.INITIAL_VERSION);
    String newVersion =
        request.version() == null || request.version().isBlank()
            ? VersionNumbering.nextVersion(latestVersion)
            : VersionNumbering.ensureValid(request.version());
    if (definition.getVersions().stream().anyMatch(v -> v.getVersion().equals(newVersion))) {
      throw new WorkflowConflictException("Workflow version already exists: " + newVersion);
    }

    Instant now = Instant.now();
    JsonNode model =
        request.model() == null || request.model().isNull()
            ? mapper.cloneModel(source.getModelJson())
            : normalizeModel(request.model(), definition.getId(), definition.getName());
    WorkflowVersionEntity versionEntity =
        newVersionEntity(workflowId, newVersion, WorkflowStatus.DRAFT, model, now);
    definition.getVersions().add(versionEntity);
    definition.setCurrentVersion(newVersion);
    definition.setStatus(WorkflowStatus.DRAFT.value());
    definition.setUpdatedAt(now);
    return mapper.toRecord(definitionRepository.save(definition));
  }

  @Transactional
  public WorkflowRecordDto updateWorkflow(String workflowId, UpdateWorkflowRequest request) {
    WorkflowDefinitionEntity definition = loadWithVersions(requireDefinition(workflowId));
    Instant now = Instant.now();
    if (request.name() != null && !request.name().isBlank()) {
      definition.setName(request.name().trim());
      definition
          .getVersions()
          .forEach(version -> updateModelName(version.getModelJson(), definition.getId(), definition.getName()));
    }
    if (request.status() != null && !request.status().isBlank()) {
      String status = WorkflowStatus.fromValue(request.status()).value();
      definition.setStatus(status);
      WorkflowVersionEntity current = requireVersionEntity(definition, definition.getCurrentVersion());
      current.setStatus(status);
      current.setUpdatedAt(now);
    }
    if (request.activeVersion() != null && !request.activeVersion().isBlank()) {
      String activeVersion = VersionNumbering.ensureValid(request.activeVersion());
      requireVersionEntity(definition, activeVersion);
      definition.setCurrentVersion(activeVersion);
    }
    definition.setUpdatedAt(now);
    return mapper.toRecord(definitionRepository.save(definition));
  }

  @Transactional
  public void delete(String workflowId) {
    requireDefinition(workflowId);
    definitionRepository.deleteById(workflowId);
  }

  public AllocateIdResponse allocateId(String kind) {
    namespaceAccess.resolveActiveNamespace();
    if (kind == null || kind.isBlank()) {
      throw new IllegalArgumentException("kind is required");
    }
    String key = kind.trim().toLowerCase(Locale.ROOT);
    if (!PlatformIdGenerator.supportedKinds().contains(key)) {
      throw new IllegalArgumentException("Unsupported id kind: " + kind);
    }
    return new AllocateIdResponse(key, idGenerator.allocate(key));
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

  private WorkflowDefinitionEntity loadWithVersions(WorkflowDefinitionEntity definition) {
    definition.getVersions().size();
    return definition;
  }

  private WorkflowVersionEntity requireVersion(String workflowId, String version) {
    return versionRepository
        .findById(new WorkflowVersionId(workflowId, version))
        .orElseThrow(() -> new WorkflowVersionNotFoundException(workflowId, version));
  }

  private WorkflowVersionEntity requireVersionEntity(
      WorkflowDefinitionEntity definition, String version) {
    return definition.getVersions().stream()
        .filter(item -> item.getVersion().equals(version))
        .findFirst()
        .orElseThrow(() -> new WorkflowVersionNotFoundException(definition.getId(), version));
  }

  private WorkflowVersionEntity newVersionEntity(
      String workflowId, String version, WorkflowStatus status, JsonNode model, Instant now) {
    WorkflowVersionEntity entity = new WorkflowVersionEntity();
    entity.setWorkflowId(workflowId);
    entity.setVersion(version);
    entity.setStatus(status.value());
    entity.setModelJson(model);
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return entity;
  }

  private JsonNode normalizeModel(JsonNode model, String workflowId, String name) {
    JsonNode cloned = mapper.cloneModel(model);
    if (cloned instanceof ObjectNode objectNode) {
      objectNode.put("id", workflowId);
      objectNode.put("name", name);
      if (objectNode.has("process") && objectNode.get("process").isObject()) {
        ObjectNode process = (ObjectNode) objectNode.get("process");
        process.put("id", workflowId.replaceFirst("^workflow_", "process_"));
        process.put("name", name);
      }
    }
    return cloned;
  }

  private void updateModelName(JsonNode model, String workflowId, String name) {
    if (model instanceof ObjectNode objectNode) {
      objectNode.put("name", name);
      if (objectNode.has("process") && objectNode.get("process").isObject()) {
        ((ObjectNode) objectNode.get("process")).put("name", name);
      }
      objectNode.put("id", workflowId);
    }
  }

  private String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
    return name.trim();
  }

  private String normalizeKeyword(String keyword) {
    return keyword == null ? "" : keyword.trim();
  }

  private int compareVersions(String left, String right) {
    int[] l = parseParts(left);
    int[] r = parseParts(right);
    for (int i = 0; i < 3; i++) {
      if (l[i] != r[i]) {
        return Integer.compare(l[i], r[i]);
      }
    }
    return 0;
  }

  private int[] parseParts(String version) {
    String[] tokens = VersionNumbering.ensureValid(version).split("\\.");
    return new int[] {
      Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2])
    };
  }
}
