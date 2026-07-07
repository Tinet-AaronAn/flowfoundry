package com.tinet.flowfoundry.workflow;

import java.util.Set;

/** Active tenant (workflow namespace) context for the current request. */
public record TenantContextDto(String tenantId, Set<String> allowedTenantIds, String tenantHeader) {}
