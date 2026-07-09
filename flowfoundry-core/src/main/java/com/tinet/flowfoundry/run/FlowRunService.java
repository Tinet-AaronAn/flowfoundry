package com.tinet.flowfoundry.run;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import com.tinet.flowfoundry.interpreter.model.InterpreterStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnFlowFoundryPlatform
public class FlowRunService {

  private final FlowRunRepository runRepository;
  private final FlowEventRepository eventRepository;
  private final FlowNodeRunRepository nodeRunRepository;

  public FlowRunService(
      FlowRunRepository runRepository,
      FlowEventRepository eventRepository,
      FlowNodeRunRepository nodeRunRepository) {
    this.runRepository = runRepository;
    this.eventRepository = eventRepository;
    this.nodeRunRepository = nodeRunRepository;
  }

  @Transactional
  public void registerRun(FlowRunContracts.FlowRunRegistration registration) {
    if (registration == null || registration.workflowId() == null || registration.workflowId().isBlank()) {
      return;
    }
    Instant now = Instant.now();
    FlowRunEntity entity =
        runRepository
            .findById(registration.workflowId())
            .orElseGet(
                () -> {
                  FlowRunEntity created = new FlowRunEntity();
                  created.setWorkflowId(registration.workflowId());
                  created.setStartedAt(now);
                  return created;
                });
    if (registration.namespace() != null && !registration.namespace().isBlank()) {
      entity.setNamespace(registration.namespace());
    } else if (entity.getNamespace() == null || entity.getNamespace().isBlank()) {
      entity.setNamespace(namespaceFromBusinessKey(registration.businessKey()));
    }
    if (registration.temporalRunId() != null && !registration.temporalRunId().isBlank()) {
      entity.setTemporalRunId(registration.temporalRunId());
    }
    if (registration.flowId() != null) {
      entity.setFlowId(registration.flowId());
    }
    if (registration.flowName() != null) {
      entity.setFlowName(registration.flowName());
    }
    if (registration.version() != null) {
      entity.setVersion(registration.version());
    }
    if (registration.businessKey() != null) {
      entity.setBusinessKey(registration.businessKey());
    }
    if (registration.runSource() != null) {
      entity.setRunSource(registration.runSource());
    }
    if (registration.input() != null) {
      entity.setInputJson(FlowRunJson.toJson(registration.input()));
    }
    if (entity.getStatus() == null || entity.getStatus().isBlank()) {
      entity.setStatus(InterpreterStatus.RUNNING.name());
    }
    entity.setLastSyncedAt(now);
    runRepository.save(entity);
  }

  @Transactional
  public void recordEvent(FlowRunEventCommand command) {
    if (command == null || command.workflowId() == null || command.workflowId().isBlank()) {
      return;
    }
    Instant occurredAt =
        command.occurredAtEpochMs() > 0
            ? Instant.ofEpochMilli(command.occurredAtEpochMs())
            : Instant.now();
    FlowRunEntity run = ensureRunHeader(command, occurredAt);

    if (command.sequenceNo() >= 0
        && !eventRepository.existsByWorkflowIdAndSequenceNo(
            command.workflowId(), command.sequenceNo())) {
      FlowEventEntity event = new FlowEventEntity();
      event.setWorkflowId(command.workflowId());
      event.setSequenceNo(command.sequenceNo());
      event.setEventType(command.eventType());
      event.setNodeId(command.nodeId());
      event.setNodeName(command.nodeName());
      event.setNodeKind(command.nodeKind());
      event.setActivityType(command.activityType());
      event.setStatus(command.status());
      event.setDetailJson(command.detailJson());
      event.setOccurredAt(occurredAt);
      eventRepository.save(event);
      upsertNodeRun(command, occurredAt);
    }

    if (command.runStatus() != null && !command.runStatus().isBlank()) {
      run.setStatus(command.runStatus());
      if (isTerminalStatus(command.runStatus())) {
        run.setCompletedAt(occurredAt);
      }
    }
    if (command.temporalRunId() != null && !command.temporalRunId().isBlank()) {
      run.setTemporalRunId(command.temporalRunId());
    }
    if (command.failureMessage() != null) {
      run.setFailureMessage(command.failureMessage());
    }
    if (command.failureType() != null) {
      run.setFailureType(command.failureType());
    }
    run.setLastSyncedAt(Instant.now());
    runRepository.save(run);
  }

  @Transactional
  public void syncRunStatus(
      String workflowId,
      String temporalRunId,
      String temporalStatus,
      String interpreterStatus,
      String failureMessage,
      String failureType) {
    if (workflowId == null || workflowId.isBlank()) {
      return;
    }
    runRepository
        .findById(workflowId)
        .ifPresent(
            run -> {
              if (temporalRunId != null && !temporalRunId.isBlank()) {
                run.setTemporalRunId(temporalRunId);
              }
              if (temporalStatus != null) {
                run.setTemporalStatus(temporalStatus);
              }
              if (interpreterStatus != null && !interpreterStatus.isBlank()) {
                run.setStatus(interpreterStatus);
                if (isTerminalStatus(interpreterStatus)) {
                  run.setCompletedAt(Instant.now());
                }
              }
              if (failureMessage != null) {
                run.setFailureMessage(failureMessage);
              }
              if (failureType != null) {
                run.setFailureType(failureType);
              }
              run.setLastSyncedAt(Instant.now());
              runRepository.save(run);
            });
  }

  @Transactional(readOnly = true)
  public FlowRunContracts.FlowRunListPage listRuns(String namespace, String keyword, int page, int size) {
    int pageIndex = Math.max(page, 0);
    int pageSize = Math.min(Math.max(size, 1), 100);
    Specification<FlowRunEntity> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (namespace != null && !namespace.isBlank()) {
            predicates.add(cb.equal(root.get("namespace"), namespace));
          }
          if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            predicates.add(
                cb.or(
                    cb.like(cb.lower(root.get("workflowId")), pattern),
                    cb.like(cb.lower(root.get("flowId")), pattern),
                    cb.like(cb.lower(root.get("flowName")), pattern),
                    cb.like(cb.lower(root.get("businessKey")), pattern)));
          }
          if (predicates.isEmpty()) {
            return cb.conjunction();
          }
          return cb.and(predicates.toArray(Predicate[]::new));
        };
    Page<FlowRunEntity> result =
        runRepository.findAll(
            spec,
            PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "startedAt")));
    return new FlowRunContracts.FlowRunListPage(
        result.stream().map(this::toListItem).toList(),
        pageIndex,
        pageSize,
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public List<FlowRunContracts.FlowRunEventDto> listEvents(String workflowId) {
    return eventRepository.findByWorkflowIdOrderBySequenceNoAsc(workflowId).stream()
        .map(this::toEventDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<FlowRunContracts.FlowNodeRunDto> listNodeRuns(String workflowId) {
    return nodeRunRepository.findByWorkflowIdOrderByStartedAtAsc(workflowId).stream()
        .map(this::toNodeRunDto)
        .toList();
  }

  public static String namespaceFromBusinessKey(String businessKey) {
    if (businessKey == null || businessKey.isBlank()) {
      return "default";
    }
    int idx = businessKey.indexOf(':');
    return idx > 0 ? businessKey.substring(0, idx) : "default";
  }

  private FlowRunEntity ensureRunHeader(FlowRunEventCommand command, Instant occurredAt) {
    return runRepository
        .findById(command.workflowId())
        .orElseGet(
            () -> {
              FlowRunEntity created = new FlowRunEntity();
              created.setWorkflowId(command.workflowId());
              created.setNamespace(
                  command.namespace() == null || command.namespace().isBlank()
                      ? namespaceFromBusinessKey(command.businessKey())
                      : command.namespace());
              created.setFlowId(command.flowId() == null ? "unknown" : command.flowId());
              created.setFlowName(command.flowName());
              created.setVersion(command.flowVersion());
              created.setBusinessKey(command.businessKey());
              created.setRunSource(
                  command.runSource() == null || command.runSource().isBlank()
                      ? "production"
                      : command.runSource());
              created.setStatus(InterpreterStatus.RUNNING.name());
              created.setStartedAt(occurredAt);
              return created;
            });
  }

  private void upsertNodeRun(FlowRunEventCommand command, Instant occurredAt) {
    if (command.nodeId() == null || command.nodeId().isBlank()) {
      return;
    }
    FlowNodeRunEntity.FlowNodeRunId id =
        new FlowNodeRunEntity.FlowNodeRunId(command.workflowId(), command.nodeId());
    FlowNodeRunEntity nodeRun =
        nodeRunRepository.findById(id).orElseGet(FlowNodeRunEntity::new);
    nodeRun.setWorkflowId(command.workflowId());
    nodeRun.setNodeId(command.nodeId());
    if (command.nodeName() != null) {
      nodeRun.setNodeName(command.nodeName());
    }
    if (command.nodeKind() != null) {
      nodeRun.setNodeKind(command.nodeKind());
    }
    if (command.activityType() != null) {
      nodeRun.setActivityType(command.activityType());
    }
    if (command.status() != null) {
      nodeRun.setStatus(command.status());
    }
    if (command.detailJson() != null) {
      nodeRun.setLastDetailJson(command.detailJson());
    }
    FlowRunEventType type = safeEventType(command.eventType());
    if (type == FlowRunEventType.NODE_ENTERED
        || type == FlowRunEventType.HUMAN_TASK_WAITING
        || type == FlowRunEventType.TIMER_WAITING) {
      if (nodeRun.getStartedAt() == null) {
        nodeRun.setStartedAt(occurredAt);
      }
      if (nodeRun.getStatus() == null || nodeRun.getStatus().isBlank()) {
        nodeRun.setStatus("RUNNING");
      }
    }
    if (type == FlowRunEventType.NODE_COMPLETED
        || type == FlowRunEventType.NODE_FAILED
        || type == FlowRunEventType.HUMAN_TASK_COMPLETED
        || type == FlowRunEventType.TIMER_FIRED
        || type == FlowRunEventType.CHILD_WORKFLOW_COMPLETED) {
      nodeRun.setCompletedAt(occurredAt);
      if (command.status() != null) {
        nodeRun.setStatus(command.status());
      }
    }
    nodeRunRepository.save(nodeRun);
  }

  private static FlowRunEventType safeEventType(String raw) {
    if (raw == null || raw.isBlank()) {
      return FlowRunEventType.NODE_ENTERED;
    }
    try {
      return FlowRunEventType.valueOf(raw);
    } catch (IllegalArgumentException e) {
      return FlowRunEventType.NODE_ENTERED;
    }
  }

  private static boolean isTerminalStatus(String status) {
    String normalized = status.toUpperCase(Locale.ROOT);
    return InterpreterStatus.COMPLETED.name().equals(normalized)
        || InterpreterStatus.FAILED.name().equals(normalized)
        || "CANCELED".equals(normalized)
        || "TERMINATED".equals(normalized)
        || "TIMED_OUT".equals(normalized);
  }

  private FlowRunContracts.FlowRunListItem toListItem(FlowRunEntity entity) {
    return new FlowRunContracts.FlowRunListItem(
        entity.getWorkflowId(),
        entity.getTemporalRunId(),
        entity.getFlowId(),
        entity.getFlowName(),
        entity.getVersion(),
        entity.getNamespace(),
        entity.getBusinessKey(),
        entity.getRunSource(),
        entity.getStatus(),
        entity.getTemporalStatus(),
        entity.getStartedAt(),
        entity.getCompletedAt());
  }

  private FlowRunContracts.FlowRunEventDto toEventDto(FlowEventEntity entity) {
    return new FlowRunContracts.FlowRunEventDto(
        entity.getId() == null ? 0L : entity.getId(),
        entity.getSequenceNo() == null ? 0 : entity.getSequenceNo(),
        entity.getEventType(),
        entity.getNodeId(),
        entity.getNodeName(),
        entity.getNodeKind(),
        entity.getActivityType(),
        entity.getStatus(),
        entity.getDetailJson(),
        entity.getOccurredAt());
  }

  private FlowRunContracts.FlowNodeRunDto toNodeRunDto(FlowNodeRunEntity entity) {
    return new FlowRunContracts.FlowNodeRunDto(
        entity.getNodeId(),
        entity.getNodeName(),
        entity.getNodeKind(),
        entity.getActivityType(),
        entity.getStatus(),
        entity.getStartedAt(),
        entity.getCompletedAt(),
        entity.getLastDetailJson());
  }
}
