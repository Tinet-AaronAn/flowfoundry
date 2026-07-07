package com.tinet.flowfoundry.security;

import com.tinet.flowfoundry.security.AdminContracts.AuditLogDto;
import com.tinet.flowfoundry.security.AdminContracts.AuditLogPageDto;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

  private final PlatformAuditLogRepository repository;

  public AuditLogService(PlatformAuditLogRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void record(AuditLogEntry entry) {
    PlatformAuditLogEntity entity = new PlatformAuditLogEntity();
    entity.setOccurredAt(entry.occurredAt() == null ? Instant.now() : entry.occurredAt());
    entity.setClientId(entry.clientId());
    entity.setActorClientId(entry.actorClientId());
    entity.setAction(entry.action());
    entity.setResourceType(entry.resourceType());
    entity.setResourceId(entry.resourceId());
    entity.setNamespace(entry.namespace());
    entity.setHttpMethod(entry.httpMethod());
    entity.setPath(entry.path());
    entity.setStatusCode(entry.statusCode());
    entity.setDetail(entry.detail());
    entity.setIpAddress(entry.ipAddress());
    repository.save(entity);
  }

  @Transactional(readOnly = true)
  public AuditLogPageDto search(
      String clientId,
      String action,
      Instant fromTime,
      Instant toTime,
      boolean includeApiCalls,
      int page,
      int size) {
    int pageIndex = Math.max(page, 0);
    int pageSize = Math.min(Math.max(size, 1), 100);
    Specification<PlatformAuditLogEntity> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (clientId != null && !clientId.isBlank()) {
            predicates.add(
                cb.or(
                    cb.equal(root.get("clientId"), clientId),
                    cb.equal(root.get("actorClientId"), clientId)));
          }
          if (action != null && !action.isBlank()) {
            predicates.add(cb.equal(root.get("action"), action));
          } else if (!includeApiCalls) {
            predicates.add(cb.notEqual(root.get("action"), AuditActions.API_CALL));
          }
          if (fromTime != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), fromTime));
          }
          if (toTime != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), toTime));
          }
          if (predicates.isEmpty()) {
            return cb.conjunction();
          }
          return cb.and(predicates.toArray(Predicate[]::new));
        };
    Page<PlatformAuditLogEntity> result =
        repository.findAll(
            spec,
            PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "occurredAt")));
    return new AuditLogPageDto(
        result.stream().map(this::toDto).toList(),
        pageIndex,
        pageSize,
        result.getTotalElements(),
        result.getTotalPages());
  }

  private AuditLogDto toDto(PlatformAuditLogEntity entity) {
    return new AuditLogDto(
        entity.getId(),
        entity.getOccurredAt(),
        entity.getClientId(),
        entity.getActorClientId(),
        entity.getAction(),
        entity.getResourceType(),
        entity.getResourceId(),
        entity.getNamespace(),
        entity.getHttpMethod(),
        entity.getPath(),
        entity.getStatusCode(),
        entity.getDetail(),
        entity.getIpAddress());
  }

  public record AuditLogEntry(
      Instant occurredAt,
      String clientId,
      String actorClientId,
      String action,
      String resourceType,
      String resourceId,
      String namespace,
      String httpMethod,
      String path,
      Integer statusCode,
      String detail,
      String ipAddress) {}
}
