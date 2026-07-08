package com.tinet.flowfoundry.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.tinet.flowfoundry.security.AdminContracts.ApiKeyDto;
import com.tinet.flowfoundry.security.AdminContracts.CreateApiKeyRequest;
import com.tinet.flowfoundry.security.AdminContracts.CreateApiKeyResponse;
import com.tinet.flowfoundry.security.AdminContracts.UpdateApiKeyRequest;
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
public class ApiKeyService {

  private static final Pattern API_KEY_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,62}$");

  private final PlatformApiKeyRepository repository;
  private final AuditLogService auditLogService;
  private final AdminAccessService adminAccessService;

  @PersistenceContext private EntityManager entityManager;

  public ApiKeyService(
      PlatformApiKeyRepository repository,
      AuditLogService auditLogService,
      AdminAccessService adminAccessService) {
    this.repository = repository;
    this.auditLogService = auditLogService;
    this.adminAccessService = adminAccessService;
  }

  @Transactional(readOnly = true)
  public Optional<AuthenticatedApiKey> authenticate(String rawKey) {
    if (rawKey == null || rawKey.isBlank()) {
      return Optional.empty();
    }
    return repository
        .findByKeyHashAndStatus(ApiKeyHasher.hash(rawKey), ApiKeyStatus.ACTIVE)
        .map(this::toAuthenticated);
  }

  @Transactional
  public void touchLastUsed(String apiKeyId) {
    repository
        .findById(apiKeyId)
        .ifPresent(
            entity -> {
              entity.setLastUsedAt(Instant.now());
              repository.save(entity);
            });
  }

  @Transactional(readOnly = true)
  public List<ApiKeyDto> list() {
    return repository.findAll().stream().map(this::toDto).toList();
  }

  /**
   * 按选中 namespace 过滤：仅返回作用域包含该 namespace 的 Key；管理员 Key（可访问全部 namespace）始终展示。
   */
  @Transactional(readOnly = true)
  public List<ApiKeyDto> listByNamespace(String namespace) {
    if (namespace == null || namespace.isBlank()) {
      return list();
    }
    return repository.findAll().stream()
        .map(this::toDto)
        .filter(
            apiKey ->
                apiKey.admin()
                    || (apiKey.namespaces() != null && apiKey.namespaces().contains(namespace)))
        .toList();
  }

  @Transactional(readOnly = true)
  public ApiKeyDto get(String apiKeyId) {
    return toDto(requireApiKey(apiKeyId));
  }

  @Transactional
  public CreateApiKeyResponse create(CreateApiKeyRequest request) {
    adminAccessService.requireAdmin();
    String apiKeyId = normalizeApiKeyId(request.id());
    if (repository.existsById(apiKeyId)) {
      throw new IllegalArgumentException("API key already exists: " + apiKeyId);
    }
    Set<String> namespaces = normalizeNamespaces(request.namespaces(), request.admin());
    String rawKey = ApiKeyGenerator.generate();
    Instant now = Instant.now();

    PlatformApiKeyEntity entity = new PlatformApiKeyEntity();
    entity.setId(apiKeyId);
    entity.setDisplayName(requireDisplayName(request.displayName()));
    entity.setDescription(trimToNull(request.description()));
    entity.setStatus(ApiKeyStatus.ACTIVE);
    entity.setAdmin(request.admin());
    entity.setKeyHash(ApiKeyHasher.hash(rawKey));
    entity.setKeyPrefix(ApiKeyGenerator.prefix(rawKey));
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    entity.setNamespaces(namespaces);
    PlatformApiKeyEntity saved = repository.save(entity);

    auditLogService.record(
        new AuditLogService.AuditLogEntry(
            now,
            apiKeyId,
            adminAccessService.actorApiKeyId(),
            AuditActions.API_KEY_CREATED,
            "api_key",
            apiKeyId,
            namespaces.stream().findFirst().orElse(null),
            null,
            null,
            null,
            "displayName=" + saved.getDisplayName(),
            null));

    return new CreateApiKeyResponse(toDto(saved), rawKey);
  }

  @Transactional
  public ApiKeyDto update(String apiKeyId, UpdateApiKeyRequest request) {
    adminAccessService.requireAdmin();
    PlatformApiKeyEntity entity = requireApiKey(apiKeyId);
    Instant now = Instant.now();
    if (request.displayName() != null && !request.displayName().isBlank()) {
      entity.setDisplayName(requireDisplayName(request.displayName()));
    }
    if (request.description() != null) {
      entity.setDescription(trimToNull(request.description()));
    }
    if (request.status() != null && !request.status().isBlank()) {
      entity.setStatus(ApiKeyStatus.normalize(request.status()));
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
    PlatformApiKeyEntity saved = repository.save(entity);

    auditLogService.record(
        new AuditLogService.AuditLogEntry(
            now,
            apiKeyId,
            adminAccessService.actorApiKeyId(),
            AuditActions.API_KEY_UPDATED,
            "api_key",
            apiKeyId,
            saved.getNamespaces().stream().findFirst().orElse(null),
            null,
            null,
            null,
            "status=" + saved.getStatus(),
            null));
    return toDto(saved);
  }

  @Transactional
  public ApiKeyDto disable(String apiKeyId) {
    adminAccessService.requireAdmin();
    PlatformApiKeyEntity entity = requireApiKey(apiKeyId);
    Instant now = Instant.now();
    entity.setStatus(ApiKeyStatus.DISABLED);
    entity.setUpdatedAt(now);
    PlatformApiKeyEntity saved = repository.save(entity);

    auditLogService.record(
        new AuditLogService.AuditLogEntry(
            now,
            apiKeyId,
            adminAccessService.actorApiKeyId(),
            AuditActions.API_KEY_DISABLED,
            "api_key",
            apiKeyId,
            null,
            null,
            null,
            null,
            null,
            null));
    return toDto(saved);
  }

  @Transactional
  public void delete(String apiKeyId) {
    adminAccessService.requireAdmin();
    if (isProtectedApiKey(apiKeyId)) {
      throw new IllegalArgumentException("Cannot delete protected API key: " + apiKeyId);
    }
    PlatformApiKeyEntity entity = requireApiKey(apiKeyId);
    Instant now = Instant.now();
    String displayName = entity.getDisplayName();
    Instant lastUsedAt = entity.getLastUsedAt();

    repository.delete(entity);

    auditLogService.record(
        new AuditLogService.AuditLogEntry(
            now,
            apiKeyId,
            adminAccessService.actorApiKeyId(),
            AuditActions.API_KEY_DELETED,
            "api_key",
            apiKeyId,
            null,
            null,
            null,
            null,
            "displayName=" + displayName + ",lastUsedAt=" + lastUsedAt,
            null));
  }

  private static boolean isProtectedApiKey(String apiKeyId) {
    return ApiKeyBootstrapRunner.ADMIN_API_KEY_ID.equals(apiKeyId);
  }

  @Transactional
  public PlatformApiKeyEntity upsertFromBootstrap(
      String apiKeyId,
      String displayName,
      String rawKey,
      boolean admin,
      List<String> namespaces) {
    Instant now = Instant.now();
    entityManager.flush();
    PlatformApiKeyEntity entity = repository.findById(apiKeyId).orElse(null);
    String keyHash = ApiKeyHasher.hash(rawKey);
    Optional<PlatformApiKeyEntity> hashOwner = repository.findByKeyHash(keyHash);
    if (hashOwner.isPresent() && (entity == null || !hashOwner.get().getId().equals(apiKeyId))) {
      PlatformApiKeyEntity owner = hashOwner.get();
      owner.setDisplayName(displayName);
      owner.setAdmin(admin);
      owner.setNamespaces(new LinkedHashSet<>(normalizeNamespaces(namespaces, admin)));
      owner.setStatus(ApiKeyStatus.ACTIVE);
      owner.setUpdatedAt(now);
      return repository.save(owner);
    }
    if (entity == null) {
      entity = new PlatformApiKeyEntity();
      entity.setId(apiKeyId);
      entity.setCreatedAt(now);
    } else if (keyHash.equals(entity.getKeyHash())) {
      entity.setDisplayName(displayName);
      entity.setAdmin(admin);
      entity.setNamespaces(new LinkedHashSet<>(normalizeNamespaces(namespaces, admin)));
      entity.setStatus(ApiKeyStatus.ACTIVE);
      entity.setUpdatedAt(now);
      return repository.save(entity);
    }
    entity.setDisplayName(displayName);
    entity.setDescription("Bootstrapped from configuration");
    entity.setStatus(ApiKeyStatus.ACTIVE);
    entity.setAdmin(admin);
    entity.setKeyHash(keyHash);
    entity.setKeyPrefix(ApiKeyGenerator.prefix(rawKey));
    entity.setNamespaces(new LinkedHashSet<>(normalizeNamespaces(namespaces, admin)));
    entity.setUpdatedAt(now);
    return repository.save(entity);
  }

  private PlatformApiKeyEntity requireApiKey(String apiKeyId) {
    return repository.findById(apiKeyId).orElseThrow(() -> new ApiKeyNotFoundException(apiKeyId));
  }

  private AuthenticatedApiKey toAuthenticated(PlatformApiKeyEntity entity) {
    return new AuthenticatedApiKey(
        entity.getId(), Set.copyOf(entity.getNamespaces()), entity.isAdmin());
  }

  private ApiKeyDto toDto(PlatformApiKeyEntity entity) {
    return new ApiKeyDto(
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

  private static String normalizeApiKeyId(String apiKeyId) {
    if (apiKeyId == null || apiKeyId.isBlank()) {
      throw new IllegalArgumentException("id is required");
    }
    String normalized = apiKeyId.trim().toLowerCase(Locale.ROOT);
    if (!API_KEY_ID_PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException(
          "id must match [a-z][a-z0-9-]{1,62}: " + apiKeyId);
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
      throw new IllegalArgumentException("namespaces is required for non-admin API keys");
    }
    Set<String> normalized = new LinkedHashSet<>();
    for (String namespace : namespaces) {
      if (namespace == null || namespace.isBlank()) {
        continue;
      }
      normalized.add(namespace.trim());
    }
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("namespaces is required for non-admin API keys");
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

  public record AuthenticatedApiKey(String apiKeyId, Set<String> namespaces, boolean admin) {}
}
