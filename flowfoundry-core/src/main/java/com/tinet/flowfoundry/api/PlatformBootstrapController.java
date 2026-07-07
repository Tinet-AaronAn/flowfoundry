package com.tinet.flowfoundry.api;

import com.tinet.flowfoundry.config.FlowFoundryProperties;
import com.tinet.flowfoundry.config.StaticAssetVersion;
import com.tinet.flowfoundry.security.PlatformSecurityHeaders;
import com.tinet.flowfoundry.security.ApiClientBootstrapRunner;
import com.tinet.flowfoundry.security.PlatformSecurityProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform")
public class PlatformBootstrapController {

  private final PlatformSecurityProperties securityProperties;
  private final FlowFoundryProperties flowFoundryProperties;
  private final StaticAssetVersion staticAssetVersion;

  public PlatformBootstrapController(
      PlatformSecurityProperties securityProperties,
      FlowFoundryProperties flowFoundryProperties,
      StaticAssetVersion staticAssetVersion) {
    this.securityProperties = securityProperties;
    this.flowFoundryProperties = flowFoundryProperties;
    this.staticAssetVersion = staticAssetVersion;
  }

  @GetMapping("/public-config")
  public ResponseEntity<Map<String, Object>> publicConfig() {
    FlowFoundryProperties.Modeler modeler = flowFoundryProperties.getModeler();
    Map<String, Object> modelerConfig = new LinkedHashMap<>();
    modelerConfig.put("embedEnabled", modeler.isEmbedEnabled());
    modelerConfig.put("apiBase", modeler.getApiBase());
    modelerConfig.put("embedPath", modeler.getEmbedPath());
    modelerConfig.put("sdkScriptPath", modeler.getSdkScriptPath());
    modelerConfig.put("allowFrameEmbedding", modeler.isAllowFrameEmbedding());

    Map<String, Object> config = new LinkedHashMap<>();
    config.put("securityEnabled", securityProperties.enabled());
    config.put("devNamespace", securityProperties.devNamespace());
    config.put("defaultTenantId", securityProperties.devNamespace());
    config.put("tenantHeader", PlatformSecurityHeaders.TENANT_ID);
    config.put("namespaceHeader", PlatformSecurityHeaders.PLATFORM_NAMESPACE);
    config.put("staticAssetVersion", staticAssetVersion.value());
    config.put("modeler", modelerConfig);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(config);
  }

  @GetMapping(value = "/auth.js", produces = "application/javascript")
  public ResponseEntity<String> authScript() {
    if (!securityProperties.enabled()) {
      return noStoreJs("// FlowFoundry security disabled");
    }
    String apiKey = resolveBrowserApiKey();
    String namespace = securityProperties.devNamespace();
    if (apiKey.isBlank()) {
      return noStoreJs("// FlowFoundry API key not configured");
    }
    return noStoreJs(
        "window.FLOWFOUNDRY_API_KEY="
            + jsonString(apiKey)
            + ";window.FLOWFOUNDRY_TENANT_ID="
            + jsonString(namespace)
            + ";window.FLOWFOUNDRY_NAMESPACE="
            + jsonString(namespace)
            + ";");
  }

  private static ResponseEntity<String> noStoreJs(String body) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.valueOf("application/javascript"))
        .body(body);
  }

  private String resolveBrowserApiKey() {
    String bootstrapKey = securityProperties.bootstrapAdminKey();
    if (bootstrapKey != null && !bootstrapKey.isBlank()) {
      return bootstrapKey.trim();
    }
    return securityProperties.apiKeys().stream()
        .filter(
            client ->
                ApiClientBootstrapRunner.ADMIN_CLIENT_ID.equals(client.clientId()) || client.admin())
        .map(PlatformSecurityProperties.ApiKeyClientProperties::key)
        .filter(key -> key != null && !key.isBlank())
        .map(String::trim)
        .findFirst()
        .orElse("");
  }

  private static String jsonString(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
