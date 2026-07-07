package com.tinet.flowfoundry.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tinet.flowfoundry.security.AdminContracts.CreateApiClientRequest;
import com.tinet.flowfoundry.security.AdminContracts.UpdateApiClientRequest;
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
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = "flowfoundry.security.enabled=true")
@Import({
  JacksonAutoConfiguration.class,
  ApiClientService.class,
  AuditLogService.class,
  AdminAccessService.class,
  PlatformSecurityProperties.class
})
class ApiClientServiceTest {

  @Autowired private ApiClientService apiClientService;

  @BeforeEach
  void setUp() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void createsClientAndAuthenticatesByDatabaseKey() {
    var created =
        apiClientService.create(
            new CreateApiClientRequest(
                "demo-app", "Demo App", "test", false, List.of("demo-ns")));
    assertThat(created.apiKey()).startsWith("ffk_");

    var authenticated = apiClientService.authenticate(created.apiKey());
    assertThat(authenticated).isPresent();
    assertThat(authenticated.get().clientId()).isEqualTo("demo-app");
    assertThat(authenticated.get().namespaces()).containsExactly("demo-ns");
  }

  @Test
  void updatesClientStatusAndNamespaces() {
    apiClientService.create(
        new CreateApiClientRequest(
            "test-client", "Test Client", null, false, List.of("demo-ns")));
    apiClientService.disable("test-client");

    var updated =
        apiClientService.update(
            "test-client",
            new UpdateApiClientRequest(
                "Test Client",
                null,
                ApiClientStatus.ACTIVE,
                false,
                List.of("demo-ns", "other-ns")));

    assertThat(updated.status()).isEqualTo(ApiClientStatus.ACTIVE);
    assertThat(updated.namespaces()).containsExactlyInAnyOrder("demo-ns", "other-ns");
  }

  @Test
  void deletesClientPermanently() {
    apiClientService.create(
        new CreateApiClientRequest("temp-app", "Temp App", null, false, List.of("demo-ns")));
    apiClientService.delete("temp-app");
    assertThat(apiClientService.list()).noneMatch(client -> "temp-app".equals(client.id()));
  }

  @Test
  void rejectsDeletingProtectedClients() {
    apiClientService.create(
        new CreateApiClientRequest(
            "platform-admin", "Platform Admin", null, true, List.of()));
    assertThatThrownBy(() -> apiClientService.delete("platform-admin"))
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
                apiClientService.create(
                    new CreateApiClientRequest("x", "X", null, false, List.of("ns"))))
        .isInstanceOf(AdminAccessDeniedException.class);
  }
}
