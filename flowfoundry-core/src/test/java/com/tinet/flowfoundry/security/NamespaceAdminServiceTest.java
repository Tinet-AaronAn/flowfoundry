package com.tinet.flowfoundry.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tinet.flowfoundry.config.NamespaceRoutingProperties;
import com.tinet.flowfoundry.security.AdminContracts.CreateNamespaceRequest;
import com.tinet.flowfoundry.security.AdminContracts.UpdateNamespaceRequest;
import com.tinet.flowfoundry.workflow.WorkflowDefinitionEntity;
import com.tinet.flowfoundry.workflow.WorkflowDefinitionRepository;
import com.tinet.flowfoundry.workflow.WorkflowStatus;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({
  NamespaceAdminService.class,
  NamespaceRoutingProperties.class,
  AdminAccessService.class,
  PlatformSecurityProperties.class,
  AuditLogService.class
})
class NamespaceAdminServiceTest {
  @Autowired private NamespaceAdminService namespaceAdminService;
  @Autowired private PlatformNamespaceRepository namespaceRepository;
  @Autowired private WorkflowDefinitionRepository workflowRepository;
  @Autowired private PlatformApiKeyRepository apiKeyRepository;

  @BeforeEach
  void seedSystemNamespace() {
    namespaceAdminService.ensureRegistered("test-ns", "Test System", "System namespace");
  }

  @Test
  void createsAndListsNamespace() {
    var created =
        namespaceAdminService.create(
            new CreateNamespaceRequest("call-campaign", "Call Campaign", "Outbound calls"));
    assertThat(created.id()).isEqualTo("call-campaign");
    assertThat(created.displayName()).isEqualTo("Call Campaign");
    assertThat(namespaceAdminService.list()).extracting("id").contains("call-campaign");
  }

  @Test
  void updatesNamespaceMetadata() {
    namespaceAdminService.create(new CreateNamespaceRequest("alpha", "Alpha", null));
    var updated =
        namespaceAdminService.update("alpha", new UpdateNamespaceRequest("Alpha Updated", "desc"));
    assertThat(updated.displayName()).isEqualTo("Alpha Updated");
    assertThat(updated.description()).isEqualTo("desc");
  }

  @Test
  void rejectsUpdatingSystemNamespace() {
    assertThatThrownBy(
            () ->
                namespaceAdminService.update(
                    "test-ns", new UpdateNamespaceRequest("Renamed", "desc")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("System namespace");
  }

  @Test
  void rejectsDeletingSystemNamespace() {
    assertThatThrownBy(() -> namespaceAdminService.delete("test-ns"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("System namespace");
  }

  @Test
  void rejectsDeletingNamespaceWithWorkflows() {
    namespaceAdminService.create(new CreateNamespaceRequest("busy-ns", "Busy", null));
    WorkflowDefinitionEntity workflow = new WorkflowDefinitionEntity();
    workflow.setId("workflow_test");
    workflow.setName("Demo");
    workflow.setNamespace("busy-ns");
    workflow.setStatus(WorkflowStatus.DRAFT.value());
    workflow.setCurrentVersion("1.0.0");
    workflow.setCreatedAt(Instant.now());
    workflow.setUpdatedAt(Instant.now());
    workflowRepository.save(workflow);

    assertThatThrownBy(() -> namespaceAdminService.delete("busy-ns"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("workflows");
  }

  @Test
  void rejectsDeletingNamespaceReferencedByApiKey() {
    namespaceAdminService.create(new CreateNamespaceRequest("keyed-ns", "Keyed", null));
    PlatformApiKeyEntity apiKey = new PlatformApiKeyEntity();
    apiKey.setId("app-key");
    apiKey.setDisplayName("App");
    apiKey.setStatus(ApiKeyStatus.ACTIVE);
    apiKey.setAdmin(false);
    apiKey.setKeyHash("hash");
    apiKey.setKeyPrefix("prefix");
    apiKey.setCreatedAt(Instant.now());
    apiKey.setUpdatedAt(Instant.now());
    apiKey.setNamespaces(Set.of("keyed-ns"));
    apiKeyRepository.save(apiKey);

    assertThatThrownBy(() -> namespaceAdminService.delete("keyed-ns"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("API keys");
  }

  @Test
  void deletesUnusedNamespace() {
    namespaceAdminService.create(new CreateNamespaceRequest("temp-ns", "Temp", null));
    assertThat(namespaceRepository.existsById("temp-ns")).isTrue();
    namespaceAdminService.delete("temp-ns");
    assertThat(namespaceRepository.existsById("temp-ns")).isFalse();
  }
}
