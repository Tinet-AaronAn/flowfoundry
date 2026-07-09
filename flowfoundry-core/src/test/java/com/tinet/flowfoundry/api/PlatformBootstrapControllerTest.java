package com.tinet.flowfoundry.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tinet.flowfoundry.config.FlowFoundryProperties;
import com.tinet.flowfoundry.config.NamespaceRoutingProperties;
import com.tinet.flowfoundry.config.StaticAssetVersion;
import com.tinet.flowfoundry.config.TemporalProperties;
import com.tinet.flowfoundry.security.PlatformSecurityProperties;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PlatformBootstrapControllerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    PlatformSecurityProperties security = new PlatformSecurityProperties();
    NamespaceRoutingProperties namespaceRouting = new NamespaceRoutingProperties();

    FlowFoundryProperties flowFoundry = new FlowFoundryProperties();
    FlowFoundryProperties.Modeler modeler = new FlowFoundryProperties.Modeler();
    modeler.setEmbedEnabled(true);
    modeler.setEmbedPath("/modeler/embed.html");
    flowFoundry.setModeler(modeler);

    TemporalProperties temporal = new TemporalProperties(
        "127.0.0.1:7233", "call-campaign", "flowfoundry-platform", 50, 100, "http://127.0.0.1:8080");

    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new PlatformBootstrapController(
                    security,
                    namespaceRouting,
                    flowFoundry,
                    temporal,
                    new StaticAssetVersion(Optional.empty())))
            .build();
  }

  @Test
  void publicConfigIncludesModelerSdkMetadata() throws Exception {
    mockMvc
        .perform(get("/api/platform/public-config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.staticAssetVersion").value("dev"))
        .andExpect(jsonPath("$.modeler.embedEnabled").value(true))
        .andExpect(jsonPath("$.modeler.embedPath").value("/modeler/embed.html"))
        .andExpect(jsonPath("$.modeler.apiBase").value("/api"))
        .andExpect(jsonPath("$.modeler.sdkScriptPath").value("/assets/js/flowfoundry-modeler-sdk.js"))
        .andExpect(jsonPath("$.defaultNamespace").value("flowfoundry-system"))
        .andExpect(jsonPath("$.namespaceHeader").value("X-Platform-Namespace"))
        .andExpect(jsonPath("$.temporal.namespace").value("call-campaign"))
        .andExpect(jsonPath("$.temporal.uiBaseUrl").value("http://127.0.0.1:8080"));
  }

  @Test
  void authScriptUsesConfiguredAdminApiKey() throws Exception {
    PlatformSecurityProperties security = new PlatformSecurityProperties();
    NamespaceRoutingProperties namespaceRouting = new NamespaceRoutingProperties();
    namespaceRouting.setSystem("flowfoundry-system");
    PlatformSecurityProperties.ApiKeyProperties adminKey =
        new PlatformSecurityProperties.ApiKeyProperties();
    adminKey.setId("platform-admin");
    adminKey.setKey("local-admin-key");
    adminKey.setAdmin(true);
    security.setApiKeys(java.util.List.of(adminKey));

    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new PlatformBootstrapController(
                    security,
                    namespaceRouting,
                    new FlowFoundryProperties(),
                    new TemporalProperties(
                        "127.0.0.1:7233", "default", "flowfoundry-platform", 50, 100, null),
                    new StaticAssetVersion(Optional.empty())))
            .build();

    mockMvc
        .perform(get("/api/platform/auth.js"))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(
                    org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString(
                            "window.FLOWFOUNDRY_API_KEY=\"local-admin-key\""),
                        org.hamcrest.Matchers.containsString(
                            "window.FLOWFOUNDRY_NAMESPACE=\"flowfoundry-system\""))));
  }
}
