package com.tinet.flowfoundry.security;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import com.tinet.flowfoundry.security.PlatformSecurityProperties.ApiKeyClientProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnFlowFoundryPlatform
public class ApiClientBootstrapRunner {

  private static final Logger log = LoggerFactory.getLogger(ApiClientBootstrapRunner.class);
  static final String LEGACY_MODELER_CLIENT_ID = "platform-modeler";
  public static final String ADMIN_CLIENT_ID = "platform-admin";

  private final PlatformSecurityProperties properties;
  private final ApiClientService apiClientService;
  private final PlatformApiClientRepository repository;

  public ApiClientBootstrapRunner(
      PlatformSecurityProperties properties,
      ApiClientService apiClientService,
      PlatformApiClientRepository repository) {
    this.properties = properties;
    this.apiClientService = apiClientService;
    this.repository = repository;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void bootstrapClients() {
    for (ApiKeyClientProperties client : properties.apiKeys()) {
      if (client.key() == null || client.key().isBlank()) {
        continue;
      }
      String clientId =
          client.clientId() == null || client.clientId().isBlank()
              ? "client-" + client.key().substring(0, Math.min(6, client.key().length()))
              : client.clientId().trim();
      String displayName =
          ADMIN_CLIENT_ID.equals(clientId) ? "Platform Admin" : clientId;
      apiClientService.upsertFromBootstrap(
          clientId,
          displayName,
          client.key().trim(),
          client.admin(),
          client.namespaces());
      log.info("Synchronized API client from configuration: {}", clientId);
    }

    if (repository.count() == 0) {
      String bootstrapAdminKey = properties.bootstrapAdminKey();
      if (bootstrapAdminKey != null && !bootstrapAdminKey.isBlank()) {
        apiClientService.upsertFromBootstrap(
            ADMIN_CLIENT_ID,
            "Platform Admin",
            bootstrapAdminKey.trim(),
            true,
            List.of());
        log.warn(
            "Created bootstrap admin API client '{}'. Change this key after first login.",
            ADMIN_CLIENT_ID);
      }
    }

    removeLegacyModelerClient();
  }

  private void removeLegacyModelerClient() {
    repository
        .findById(LEGACY_MODELER_CLIENT_ID)
        .ifPresent(
            legacy -> {
              repository.delete(legacy);
              log.info(
                  "Removed legacy API client '{}'; browser and scripts should use '{}' instead",
                  LEGACY_MODELER_CLIENT_ID,
                  ADMIN_CLIENT_ID);
            });
  }
}
