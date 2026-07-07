package com.tinet.flowfoundry.api;

import com.tinet.flowfoundry.security.AdminAccessService;
import com.tinet.flowfoundry.security.AdminContracts.ApiClientDto;
import com.tinet.flowfoundry.security.AdminContracts.AuditLogPageDto;
import com.tinet.flowfoundry.security.AdminContracts.CallerProfileDto;
import com.tinet.flowfoundry.security.AdminContracts.CreateApiClientRequest;
import com.tinet.flowfoundry.security.AdminContracts.CreateApiClientResponse;
import com.tinet.flowfoundry.security.AdminContracts.UpdateApiClientRequest;
import com.tinet.flowfoundry.security.ApiClientService;
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

  private final ApiClientService apiClientService;
  private final AuditLogService auditLogService;
  private final AdminAccessService adminAccessService;
  private final NamespaceAccessService namespaceAccessService;
  private final PlatformSecurityProperties securityProperties;

  public AdminController(
      ApiClientService apiClientService,
      AuditLogService auditLogService,
      AdminAccessService adminAccessService,
      NamespaceAccessService namespaceAccessService,
      PlatformSecurityProperties securityProperties) {
    this.apiClientService = apiClientService;
    this.auditLogService = auditLogService;
    this.adminAccessService = adminAccessService;
    this.namespaceAccessService = namespaceAccessService;
    this.securityProperties = securityProperties;
  }

  @GetMapping("/me")
  public CallerProfileDto me() {
    return new CallerProfileDto(
        adminAccessService.actorClientId(),
        adminAccessService.isLocalAdminRequest(),
        namespaceAccessService.allowedNamespaces(),
        namespaceAccessService.allowedTenantIds(),
        securityProperties.enabled());
  }

  @GetMapping("/api-clients")
  public List<ApiClientDto> listClients() {
    adminAccessService.requireAdmin();
    return apiClientService.list();
  }

  @GetMapping("/api-clients/{clientId}")
  public ApiClientDto getClient(@PathVariable String clientId) {
    adminAccessService.requireAdmin();
    return apiClientService.get(clientId);
  }

  @PostMapping("/api-clients")
  @ResponseStatus(HttpStatus.CREATED)
  public CreateApiClientResponse createClient(@RequestBody CreateApiClientRequest request) {
    return apiClientService.create(request);
  }

  @PutMapping("/api-clients/{clientId}")
  public ApiClientDto updateClient(
      @PathVariable String clientId, @RequestBody UpdateApiClientRequest request) {
    return apiClientService.update(clientId, request);
  }

  @DeleteMapping("/api-clients/{clientId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteClient(@PathVariable String clientId) {
    adminAccessService.requireAdmin();
    apiClientService.delete(clientId);
  }

  @GetMapping("/audit-logs")
  public AuditLogPageDto auditLogs(
      @RequestParam(required = false) String clientId,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(defaultValue = "false") boolean includeApiCalls,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    adminAccessService.requireAdmin();
    return auditLogService.search(clientId, action, from, to, includeApiCalls, page, size);
  }
}
