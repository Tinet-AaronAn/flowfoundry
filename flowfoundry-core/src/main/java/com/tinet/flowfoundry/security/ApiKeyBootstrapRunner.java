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

    if (repository.count() == 0) {
      String bootstrapAdminKey = properties.bootstrapAdminKey();
      if (bootstrapAdminKey != null && !bootstrapAdminKey.isBlank()) {
        apiKeyService.upsertFromBootstrap(
            ADMIN_API_KEY_ID,
            "Platform Admin",
            bootstrapAdminKey.trim(),
            true,
            List.of());
        log.warn(
            "Created bootstrap admin API key '{}'. Change this key after first login.",
            ADMIN_API_KEY_ID);
      }
    }

    removeLegacyModelerApiKey();
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
