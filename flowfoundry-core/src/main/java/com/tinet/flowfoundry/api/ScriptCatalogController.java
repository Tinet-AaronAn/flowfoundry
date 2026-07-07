package com.tinet.flowfoundry.api;

import com.tinet.flowfoundry.security.NamespaceAccessService;
import com.tinet.flowfoundry.script.ScriptCatalogResponse;
import com.tinet.flowfoundry.script.ScriptCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/script-catalog")
public class ScriptCatalogController {

  private final ScriptCatalogService scriptCatalogService;
  private final NamespaceAccessService namespaceAccess;

  public ScriptCatalogController(
      ScriptCatalogService scriptCatalogService, NamespaceAccessService namespaceAccess) {
    this.scriptCatalogService = scriptCatalogService;
    this.namespaceAccess = namespaceAccess;
  }

  @GetMapping("/scripts")
  public ScriptCatalogResponse listScripts(@RequestParam(required = false) String enterpriseId) {
    namespaceAccess.requireAuthenticatedNamespace();
    return scriptCatalogService.listScripts(enterpriseId);
  }

  @GetMapping("/scripts/{scriptCodeId}/versions")
  public ScriptCatalogResponse listVersions(
      @PathVariable String scriptCodeId,
      @RequestParam(required = false) String enterpriseId) {
    namespaceAccess.requireAuthenticatedNamespace();
    return scriptCatalogService.listVersions(scriptCodeId, enterpriseId);
  }
}
