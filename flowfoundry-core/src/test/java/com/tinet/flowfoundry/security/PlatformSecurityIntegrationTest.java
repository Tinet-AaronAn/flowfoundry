package com.tinet.flowfoundry.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tinet.flowfoundry.api.PlatformBootstrapController;
import com.tinet.flowfoundry.config.FlowFoundryProperties;
import com.tinet.flowfoundry.config.StaticAssetVersion;
import com.tinet.flowfoundry.config.TemporalProperties;
import java.util.Optional;
import com.tinet.flowfoundry.security.AdminContracts.CreateApiKeyRequest;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DataJpaTest
@ActiveProfiles("test")
@Import({
  JacksonAutoConfiguration.class,
  ApiKeyService.class,
  AuditLogService.class,
  AdminAccessService.class,
  PlatformSecurityProperties.class
})
class PlatformSecurityIntegrationTest {

  @Autowired private ApiKeyService apiKeyService;
  @Autowired private AuditLogService auditLogService;

  private PlatformSecurityProperties properties;
  private ApiKeyAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    properties = new PlatformSecurityProperties();
    properties.setDevNamespace("alpha");
    filter = new ApiKeyAuthenticationFilter(apiKeyService, auditLogService);
    SecurityContextHolder.getContext()
        .setAuthentication(
            CallerAuthentication.forApiKey(
                new ApiKeyService.AuthenticatedApiKey("platform-admin", java.util.Set.of(), true)));
    apiKeyService.create(
        new CreateApiKeyRequest("alpha-key", "Alpha", null, false, List.of("alpha")));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void authenticatesValidApiKey() throws ServletException, IOException {
    String key =
        apiKeyService
            .create(
                new CreateApiKeyRequest("auth-test", "Auth Test", null, false, List.of("alpha")))
            .secret();

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(PlatformSecurityHeaders.API_KEY, key);
    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

    var authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication).isInstanceOf(CallerAuthentication.class);
    assertThat(((CallerAuthentication) authentication).apiKeyId()).isEqualTo("auth-test");
  }

  @Test
  void rejectsInvalidApiKey() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(PlatformSecurityHeaders.API_KEY, "wrong-key");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void exposesPublicConfigWithoutApiKey() throws Exception {
    MockMvc mockMvc =
        MockMvcBuilders.standaloneSetup(
                new PlatformBootstrapController(
                    properties,
                    new FlowFoundryProperties(),
                    new TemporalProperties(
                        "127.0.0.1:7233", "default", "flowfoundry-platform", 50, 100, null),
                    new StaticAssetVersion(Optional.empty())))
            .build();

    mockMvc.perform(get("/api/platform/public-config")).andExpect(status().isOk());
  }
}
