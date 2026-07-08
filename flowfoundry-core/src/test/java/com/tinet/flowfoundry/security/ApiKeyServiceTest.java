package com.tinet.flowfoundry.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tinet.flowfoundry.security.AdminContracts.CreateApiKeyRequest;
import com.tinet.flowfoundry.security.AdminContracts.UpdateApiKeyRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({
  JacksonAutoConfiguration.class,
  ApiKeyService.class,
  AuditLogService.class,
  AdminAccessService.class,
  PlatformSecurityProperties.class
})
class ApiKeyServiceTest {

  @Autowired private ApiKeyService apiKeyService;

  @BeforeEach
  void setUp() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void createsApiKeyAndAuthenticatesByDatabaseKey() {
    var created =
        apiKeyService.create(
            new CreateApiKeyRequest(
                "demo-app", "Demo App", "test", false, List.of("demo-ns")));
    assertThat(created.secret()).startsWith("ffk_");

    var authenticated = apiKeyService.authenticate(created.secret());
    assertThat(authenticated).isPresent();
    assertThat(authenticated.get().apiKeyId()).isEqualTo("demo-app");
    assertThat(authenticated.get().namespaces()).containsExactly("demo-ns");
  }

  @Test
  void updatesApiKeyStatusAndNamespaces() {
    apiKeyService.create(
        new CreateApiKeyRequest(
            "test-key", "Test Key", null, false, List.of("demo-ns")));
    apiKeyService.disable("test-key");

    var updated =
        apiKeyService.update(
            "test-key",
            new UpdateApiKeyRequest(
                "Test Key",
                null,
                ApiKeyStatus.ACTIVE,
                false,
                List.of("demo-ns", "other-ns")));

    assertThat(updated.status()).isEqualTo(ApiKeyStatus.ACTIVE);
    assertThat(updated.namespaces()).containsExactlyInAnyOrder("demo-ns", "other-ns");
  }

  @Test
  void deletesApiKeyPermanently() {
    apiKeyService.create(
        new CreateApiKeyRequest("temp-app", "Temp App", null, false, List.of("demo-ns")));
    apiKeyService.delete("temp-app");
    assertThat(apiKeyService.list()).noneMatch(apiKey -> "temp-app".equals(apiKey.id()));
  }

  @Test
  void rejectsDeletingProtectedApiKeys() {
    apiKeyService.create(
        new CreateApiKeyRequest(
            "platform-admin", "Platform Admin", null, true, List.of()));
    assertThatThrownBy(() -> apiKeyService.delete("platform-admin"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("protected");
  }

  @Test
  void deniesCreateFromRemoteIp() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("10.0.0.8");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    assertThatThrownBy(
            () ->
                apiKeyService.create(
                    new CreateApiKeyRequest("x", "X", null, false, List.of("ns"))))
        .isInstanceOf(AdminAccessDeniedException.class);
  }
}
