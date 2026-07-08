package com.tinet.flowfoundry.api;

import com.tinet.flowfoundry.security.AdminAccessService;
import com.tinet.flowfoundry.security.AdminContracts.ApiKeyDto;
import com.tinet.flowfoundry.security.AdminContracts.AuditLogPageDto;
import com.tinet.flowfoundry.security.AdminContracts.CallerProfileDto;
import com.tinet.flowfoundry.security.AdminContracts.CreateApiKeyRequest;
import com.tinet.flowfoundry.security.AdminContracts.CreateApiKeyResponse;
import com.tinet.flowfoundry.security.AdminContracts.UpdateApiKeyRequest;
import com.tinet.flowfoundry.security.ApiKeyService;
import com.tinet.flowfoundry.security.AuditLogService;
import com.tinet.flowfoundry.security.NamespaceAccessService;
import com.tinet.flowfoundry.security.PlatformSecurityProperties;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

  private final ApiKeyService apiKeyService;
  private final AuditLogService auditLogService;
  private final AdminAccessService adminAccessService;
  private final NamespaceAccessService namespaceAccessService;
  private final PlatformSecurityProperties securityProperties;

  public AdminController(
      ApiKeyService apiKeyService,
      AuditLogService auditLogService,
      AdminAccessService adminAccessService,
      NamespaceAccessService namespaceAccessService,
      PlatformSecurityProperties securityProperties) {
    this.apiKeyService = apiKeyService;
    this.auditLogService = auditLogService;
    this.adminAccessService = adminAccessService;
    this.namespaceAccessService = namespaceAccessService;
    this.securityProperties = securityProperties;
  }

  @GetMapping("/me")
  public CallerProfileDto me() {
    return new CallerProfileDto(
        adminAccessService.actorApiKeyId(),
        adminAccessService.isLocalAdminRequest(),
        namespaceAccessService.allowedNamespaces(),
        securityProperties.enabled());
  }

  @GetMapping("/api-keys")
  public List<ApiKeyDto> listApiKeys(
      @RequestParam(name = "allNamespaces", defaultValue = "false") boolean allNamespaces) {
    adminAccessService.requireAdmin();
    // 默认按右上角选中 namespace 过滤；管理员可传 allNamespaces=true 查看全部。
    if (allNamespaces) {
      return apiKeyService.list();
    }
    String namespace = namespaceAccessService.namespaceContext().namespace();
    return namespace == null
        ? apiKeyService.list()
        : apiKeyService.listByNamespace(namespace);
  }

  @GetMapping("/api-keys/{apiKeyId}")
  public ApiKeyDto getApiKey(@PathVariable String apiKeyId) {
    adminAccessService.requireAdmin();
    return apiKeyService.get(apiKeyId);
  }

  @PostMapping("/api-keys")
  @ResponseStatus(HttpStatus.CREATED)
  public CreateApiKeyResponse createApiKey(@RequestBody CreateApiKeyRequest request) {
    return apiKeyService.create(request);
  }

  @PutMapping("/api-keys/{apiKeyId}")
  public ApiKeyDto updateApiKey(
      @PathVariable String apiKeyId, @RequestBody UpdateApiKeyRequest request) {
    return apiKeyService.update(apiKeyId, request);
  }

  @DeleteMapping("/api-keys/{apiKeyId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteApiKey(@PathVariable String apiKeyId) {
    adminAccessService.requireAdmin();
    apiKeyService.delete(apiKeyId);
  }

  @GetMapping("/audit-logs")
  public AuditLogPageDto auditLogs(
      @RequestParam(required = false) String apiKeyId,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(defaultValue = "false") boolean includeApiCalls,
      @RequestParam(name = "allNamespaces", defaultValue = "false") boolean allNamespaces,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    adminAccessService.requireAdmin();
    // 默认按右上角选中 namespace 过滤；管理员可传 allNamespaces=true 查看全部。
    String namespace = allNamespaces ? null : namespaceAccessService.namespaceContext().namespace();
    return auditLogService.search(
        apiKeyId, action, from, to, includeApiCalls, namespace, page, size);
  }
}
