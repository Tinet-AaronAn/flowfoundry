package com.tinet.flowfoundry.security;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import com.tinet.flowfoundry.security.PlatformSecurityProperties.ApiKeyProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnFlowFoundryPlatform
public class ApiKeyBootstrapRunner {

  private static final Logger log = LoggerFactory.getLogger(ApiKeyBootstrapRunner.class);
  static final String LEGACY_MODELER_API_KEY_ID = "platform-modeler";
  public static final String ADMIN_API_KEY_ID = "platform-admin";
  // 与配置默认值 FLOWFOUNDRY_API_KEY:local-admin-key 对齐：当没有任何管理员 Key 时兜底创建。
  static final String DEFAULT_ADMIN_KEY = "local-admin-key";

  private final PlatformSecurityProperties properties;
  private final ApiKeyService apiKeyService;
  private final PlatformApiKeyRepository repository;

  public ApiKeyBootstrapRunner(
      PlatformSecurityProperties properties,
      ApiKeyService apiKeyService,
      PlatformApiKeyRepository repository) {
    this.properties = properties;
    this.apiKeyService = apiKeyService;
    this.repository = repository;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void bootstrapApiKeys() {
    for (ApiKeyProperties apiKey : properties.apiKeys()) {
      if (apiKey.key() == null || apiKey.key().isBlank()) {
        continue;
      }
      String apiKeyId =
          apiKey.id() == null || apiKey.id().isBlank()
              ? "key-" + apiKey.key().substring(0, Math.min(6, apiKey.key().length()))
              : apiKey.id().trim();
      String displayName =
          ADMIN_API_KEY_ID.equals(apiKeyId) ? "Platform Admin" : apiKeyId;
      apiKeyService.upsertFromBootstrap(
          apiKeyId,
          displayName,
          apiKey.key().trim(),
          apiKey.admin(),
          apiKey.namespaces());
      log.info("Synchronized API key from configuration: {}", apiKeyId);
    }

    ensureAdminApiKey();
    removeLegacyModelerApiKey();
  }

  /**
   * 保证平台初始化后始终存在一个可访问全部 namespace 的管理员 Key（admin=true ⇒ namespaces 为空表示不限）。
   * 正常情况下配置里的 {@code platform-admin} 已经同步创建；此处仅在完全缺失时兜底创建默认 Key。
   */
  private void ensureAdminApiKey() {
    boolean hasActiveAdmin =
        repository.findAll().stream()
            .anyMatch(entity -> entity.isAdmin() && ApiKeyStatus.isActive(entity.getStatus()));
    if (hasActiveAdmin) {
      return;
    }
    apiKeyService.upsertFromBootstrap(
        ADMIN_API_KEY_ID, "Platform Admin", DEFAULT_ADMIN_KEY, true, List.of());
    log.warn(
        "No admin API key found; provisioned default admin key '{}'. "
            + "Set FLOWFOUNDRY_API_KEY and rotate it after first login.",
        ADMIN_API_KEY_ID);
  }

  private void removeLegacyModelerApiKey() {
    repository
        .findById(LEGACY_MODELER_API_KEY_ID)
        .ifPresent(
            legacy -> {
              repository.delete(legacy);
              log.info(
                  "Removed legacy API key '{}'; browser and scripts should use '{}' instead",
                  LEGACY_MODELER_API_KEY_ID,
                  ADMIN_API_KEY_ID);
            });
  }
}
