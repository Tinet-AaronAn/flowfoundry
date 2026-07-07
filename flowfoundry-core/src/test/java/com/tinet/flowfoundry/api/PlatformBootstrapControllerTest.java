package com.tinet.flowfoundry.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tinet.flowfoundry.config.FlowFoundryProperties;
import com.tinet.flowfoundry.config.StaticAssetVersion;
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
    security.setEnabled(false);
    security.setDevNamespace("default");

    FlowFoundryProperties flowFoundry = new FlowFoundryProperties();
    FlowFoundryProperties.Modeler modeler = new FlowFoundryProperties.Modeler();
    modeler.setEmbedEnabled(true);
    modeler.setEmbedPath("/modeler/embed.html");
    flowFoundry.setModeler(modeler);

    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new PlatformBootstrapController(
                    security, flowFoundry, new StaticAssetVersion(Optional.empty())))
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
        .andExpect(jsonPath("$.modeler.sdkScriptPath").value("/assets/js/flowfoundry-modeler-sdk.js"));
  }

  @Test
  void authScriptUsesConfiguredAdminApiKey() throws Exception {
    PlatformSecurityProperties security = new PlatformSecurityProperties();
    security.setEnabled(true);
    security.setDevNamespace("ai-collection-strategy");
    security.setBootstrapAdminKey("local-admin-key");

    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new PlatformBootstrapController(
                    security,
                    new FlowFoundryProperties(),
                    new StaticAssetVersion(Optional.empty())))
            .build();

    mockMvc
        .perform(get("/api/platform/auth.js"))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        "window.FLOWFOUNDRY_API_KEY=\"local-admin-key\"")));
  }
}
