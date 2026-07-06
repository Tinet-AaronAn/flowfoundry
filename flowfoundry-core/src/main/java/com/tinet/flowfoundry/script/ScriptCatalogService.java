package com.tinet.flowfoundry.script;

import com.tinet.flowfoundry.activity.ScriptRuntimeProperties;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ScriptCatalogService {

  private final IvrNodejsScriptCatalogClient catalogClient;
  private final ScriptRuntimeProperties runtimeProperties;

  public ScriptCatalogService(
      IvrNodejsScriptCatalogClient catalogClient, ScriptRuntimeProperties runtimeProperties) {
    this.catalogClient = catalogClient;
    this.runtimeProperties = runtimeProperties;
  }

  public ScriptCatalogResponse listScripts(String enterpriseId) {
    String resolvedEnterpriseId = resolveEnterpriseId(enterpriseId);
    if (catalogClient.isRemoteEnabled()) {
      return new ScriptCatalogResponse(
          "remote",
          catalogClient.listScripts(resolvedEnterpriseId),
          List.of());
    }
    return new ScriptCatalogResponse("stub", stubScripts(), List.of());
  }

  public ScriptCatalogResponse listVersions(String scriptCodeId, String enterpriseId) {
    String resolvedEnterpriseId = resolveEnterpriseId(enterpriseId);
    if (catalogClient.isRemoteEnabled()) {
      return new ScriptCatalogResponse(
          "remote",
          List.of(),
          catalogClient.listVersions(scriptCodeId, resolvedEnterpriseId));
    }
    return new ScriptCatalogResponse("stub", List.of(), stubVersions(scriptCodeId));
  }

  private String resolveEnterpriseId(String enterpriseId) {
    if (enterpriseId != null && !enterpriseId.isBlank()) {
      return enterpriseId.trim();
    }
    return runtimeProperties.enterpriseId() == null ? "" : runtimeProperties.enterpriseId().trim();
  }

  private static List<ScriptCatalogEntry> stubScripts() {
    return List.of(
        new ScriptCatalogEntry(
            "risk-check", "Risk Check", "Risk routing script (stub)", "1", "1", true),
        new ScriptCatalogEntry(
            "demo-script", "Demo Script", "Local demo script (stub)", "1", "1", true));
  }

  private static List<ScriptVersionEntry> stubVersions(String scriptCodeId) {
    if ("risk-check".equals(scriptCodeId)) {
      return List.of(
          new ScriptVersionEntry("1", true, true, "V1 active"));
    }
    if ("demo-script".equals(scriptCodeId)) {
      return List.of(new ScriptVersionEntry("1", true, true, "V1 active"));
    }
    return List.of(new ScriptVersionEntry("1", true, true, "V1"));
  }
}
