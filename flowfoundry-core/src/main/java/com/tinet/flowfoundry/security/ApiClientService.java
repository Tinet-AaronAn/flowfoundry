package com.tinet.flowfoundry.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.tinet.flowfoundry.security.AdminContracts.ApiClientDto;
import com.tinet.flowfoundry.security.AdminContracts.CreateApiClientRequest;
import com.tinet.flowfoundry.security.AdminContracts.CreateApiClientResponse;
import com.tinet.flowfoundry.security.AdminContracts.UpdateApiClientRequest;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiClientService {

  private static final Pattern CLIENT_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,62}$");

  private final PlatformApiClientRepository repository;
  private final AuditLogService auditLogService;
  private final AdminAccessService adminAccessService;

  @PersistenceContext private EntityManager entityManager;

  public ApiClientService(
      PlatformApiClientRepository repository,
      AuditLogService auditLogService,
      AdminAccessService adminAccessService) {
    this.repository = repository;
    this.auditLogService = auditLogService;
    this.adminAccessService = adminAccessService;
  }

  @Transactional(readOnly = true)
  public Optional<AuthenticatedApiClient> authenticate(String rawKey) {
    if (rawKey == null || rawKey.isBlank()) {
      return Optional.empty();
    }
    return repository
        .findByKeyHashAndStatus(ApiKeyHasher.hash(rawKey), ApiClientStatus.ACTIVE)
        .map(this::toAuthenticated);
  }

  @Transactional
  public void touchLastUsed(String clientId) {
    repository
        .findById(clientId)
        .ifPresent(
            entity -> {
              entity.setLastUsedAt(Instant.now());
              repository.save(entity);
            });
  }

  @Transactional(readOnly = true)
  public List<ApiClientDto> list() {
    return repository.findAll().stream().map(this::toDto).toList();
  }

  @Transactional(readOnly = true)
  public ApiClientDto get(String clientId) {
    return toDto(requireClient(clientId));
  }

  @Transactional
  public CreateApiClientResponse create(CreateApiClientRequest request) {
    adminAccessService.requireAdmin();
    String clientId = normalizeClientId(request.id());
    if (repository.existsById(clientId)) {
      throw new IllegalArgumentException("API client already exists: " + clientId);
    }
    Set<String> namespaces = normalizeNamespaces(request.namespaces(), request.admin());
    String rawKey = ApiKeyGenerator.generate();
    Instant now = Instant.now();

    PlatformApiClientEntity entity = new PlatformApiClientEntity();
    entity.setId(clientId);
    entity.setDisplayName(requireDisplayName(request.displayName()));
    entity.setDescription(trimToNull(request.description()));
    entity.setStatus(ApiClientStatus.ACTIVE);
    entity.setAdmin(request.admin());
    entity.setKeyHash(ApiKeyHasher.hash(rawKey));
    entity.setKeyPrefix(ApiKeyGenerator.prefix(rawKey));
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    entity.setNamespaces(namespaces);
    PlatformApiClientEntity saved = repository.save(entity);

    auditLogService.record(
        new AuditLogService.AuditLogEntry(
            now,
            clientId,
            adminAccessService.actorClientId(),
            AuditActions.CLIENT_CREATED,
            "api_client",
            clientId,
            namespaces.stream().findFirst().orElse(null),
            null,
            null,
            null,
            "displayName=" + saved.getDisplayName(),
            null));

    return new CreateApiClientResponse(toDto(saved), rawKey);
  }

  @Transactional
  public ApiClientDto update(String clientId, UpdateApiClientRequest request) {
    adminAccessService.requireAdmin();
    PlatformApiClientEntity entity = requireClient(clientId);
    Instant now = Instant.now();
    if (request.displayName() != null && !request.displayName().isBlank()) {
      entity.setDisplayName(requireDisplayName(request.displayName()));
    }
    if (request.description() != null) {
      entity.setDescription(trimToNull(request.description()));
    }
    if (request.status() != null && !request.status().isBlank()) {
      entity.setStatus(ApiClientStatus.normalize(request.status()));
    }
    if (request.admin() != null) {
      entity.setAdmin(request.admin());
    }
    if (Boolean.TRUE.equals(entity.isAdmin())) {
      entity.setNamespaces(new LinkedHashSet<>());
    } else if (request.namespaces() != null) {
      entity.setNamespaces(normalizeNamespaces(request.namespaces(), false));
    }
    entity.setUpdatedAt(now);
    PlatformApiClientEntity saved = repository.save(entity);

    auditLogService.record(
        new AuditLogService.AuditLogEntry(
            now,
            clientId,
            adminAccessService.actorClientId(),
            AuditActions.CLIENT_UPDATED,
            "api_client",
            clientId,
            saved.getNamespaces().stream().findFirst().orElse(null),
            null,
            null,
            null,
            "status=" + saved.getStatus(),
            null));
    return toDto(saved);
  }

  @Transactional
  public ApiClientDto disable(String clientId) {
    adminAccessService.requireAdmin();
    PlatformApiClientEntity entity = requireClient(clientId);
    Instant now = Instant.now();
    entity.setStatus(ApiClientStatus.DISABLED);
    entity.setUpdatedAt(now);
    PlatformApiClientEntity saved = repository.save(entity);

    auditLogService.record(
        new AuditLogService.AuditLogEntry(
            now,
            clientId,
            adminAccessService.actorClientId(),
            AuditActions.CLIENT_DISABLED,
            "api_client",
            clientId,
            null,
            null,
            null,
            null,
            null,
            null));
    return toDto(saved);
  }

  @Transactional
  public void delete(String clientId) {
    adminAccessService.requireAdmin();
    if (isProtectedClient(clientId)) {
      throw new IllegalArgumentException("Cannot delete protected API client: " + clientId);
    }
    PlatformApiClientEntity entity = requireClient(clientId);
    Instant now = Instant.now();
    String displayName = entity.getDisplayName();
    Instant lastUsedAt = entity.getLastUsedAt();

    repository.delete(entity);

    auditLogService.record(
        new AuditLogService.AuditLogEntry(
            now,
            clientId,
            adminAccessService.actorClientId(),
            AuditActions.CLIENT_DELETED,
            "api_client",
            clientId,
            null,
            null,
            null,
            null,
            "displayName=" + displayName + ",lastUsedAt=" + lastUsedAt,
            null));
  }

  private static boolean isProtectedClient(String clientId) {
    return ApiClientBootstrapRunner.ADMIN_CLIENT_ID.equals(clientId);
  }

  @Transactional
  public PlatformApiClientEntity upsertFromBootstrap(
      String clientId,
      String displayName,
      String rawKey,
      boolean admin,
      List<String> namespaces) {
    Instant now = Instant.now();
    entityManager.flush();
    PlatformApiClientEntity entity = repository.findById(clientId).orElse(null);
    String keyHash = ApiKeyHasher.hash(rawKey);
    Optional<PlatformApiClientEntity> hashOwner = repository.findByKeyHash(keyHash);
    if (hashOwner.isPresent() && (entity == null || !hashOwner.get().getId().equals(clientId))) {
      PlatformApiClientEntity owner = hashOwner.get();
      owner.setDisplayName(displayName);
      owner.setAdmin(admin);
      owner.setNamespaces(new LinkedHashSet<>(normalizeNamespaces(namespaces, admin)));
      owner.setStatus(ApiClientStatus.ACTIVE);
      owner.setUpdatedAt(now);
      return repository.save(owner);
    }
    if (entity == null) {
      entity = new PlatformApiClientEntity();
      entity.setId(clientId);
      entity.setCreatedAt(now);
    } else if (keyHash.equals(entity.getKeyHash())) {
      entity.setDisplayName(displayName);
      entity.setAdmin(admin);
      entity.setNamespaces(new LinkedHashSet<>(normalizeNamespaces(namespaces, admin)));
      entity.setStatus(ApiClientStatus.ACTIVE);
      entity.setUpdatedAt(now);
      return repository.save(entity);
    }
    entity.setDisplayName(displayName);
    entity.setDescription("Bootstrapped from configuration");
    entity.setStatus(ApiClientStatus.ACTIVE);
    entity.setAdmin(admin);
    entity.setKeyHash(keyHash);
    entity.setKeyPrefix(ApiKeyGenerator.prefix(rawKey));
    entity.setNamespaces(new LinkedHashSet<>(normalizeNamespaces(namespaces, admin)));
    entity.setUpdatedAt(now);
    return repository.save(entity);
  }

  private PlatformApiClientEntity requireClient(String clientId) {
    return repository.findById(clientId).orElseThrow(() -> new ApiClientNotFoundException(clientId));
  }

  private AuthenticatedApiClient toAuthenticated(PlatformApiClientEntity entity) {
    return new AuthenticatedApiClient(
        entity.getId(), Set.copyOf(entity.getNamespaces()), entity.isAdmin());
  }

  private ApiClientDto toDto(PlatformApiClientEntity entity) {
    return new ApiClientDto(
        entity.getId(),
        entity.getDisplayName(),
        entity.getDescription(),
        entity.getStatus(),
        entity.isAdmin(),
        entity.getKeyPrefix(),
        Set.copyOf(entity.getNamespaces()),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getLastUsedAt());
  }

  private static String normalizeClientId(String clientId) {
    if (clientId == null || clientId.isBlank()) {
      throw new IllegalArgumentException("id is required");
    }
    String normalized = clientId.trim().toLowerCase(Locale.ROOT);
    if (!CLIENT_ID_PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException(
          "id must match [a-z][a-z0-9-]{1,62}: " + clientId);
    }
    return normalized;
  }

  private static String requireDisplayName(String displayName) {
    if (displayName == null || displayName.isBlank()) {
      throw new IllegalArgumentException("displayName is required");
    }
    return displayName.trim();
  }

  private static Set<String> normalizeNamespaces(List<String> namespaces, boolean admin) {
    if (admin) {
      return new LinkedHashSet<>();
    }
    if (namespaces == null || namespaces.isEmpty()) {
      throw new IllegalArgumentException("namespaces is required for non-admin clients");
    }
    Set<String> normalized = new LinkedHashSet<>();
    for (String namespace : namespaces) {
      if (namespace == null || namespace.isBlank()) {
        continue;
      }
      normalized.add(namespace.trim());
    }
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("namespaces is required for non-admin clients");
    }
    return normalized;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  public record AuthenticatedApiClient(String clientId, Set<String> namespaces, boolean admin) {}
}
