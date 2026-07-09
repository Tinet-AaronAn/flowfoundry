package com.tinet.flowfoundry.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tinet.flowfoundry.security.AdminContracts.CreateNamespaceRequest;
import com.tinet.flowfoundry.security.AdminContracts.UpdateNamespaceRequest;
import com.tinet.flowfoundry.workflow.WorkflowDefinitionEntity;
import com.tinet.flowfoundry.workflow.WorkflowDefinitionRepository;
import com.tinet.flowfoundry.workflow.WorkflowStatus;
import java.time.Instant;
import java.util.List;
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
  void seedNamespace() {
    namespaceAdminService.ensureRegistered("test-ns", "Test NS", "Test namespace");
  }

  @Test
  void createsAndListsNamespace() {
    var created =
        namespaceAdminService.create(
            new CreateNamespaceRequest("demo-app", "Demo App", "Second namespace"));
    assertThat(created.id()).isEqualTo("demo-app");
    assertThat(created.displayName()).isEqualTo("Demo App");
    assertThat(namespaceAdminService.list()).extracting("id").contains("demo-app", "ai-collection-strategy");
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
  void allowsUpdatingRegisteredNamespace() {
    var updated =
        namespaceAdminService.update("test-ns", new UpdateNamespaceRequest("Renamed", "desc"));
    assertThat(updated.displayName()).isEqualTo("Renamed");
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
