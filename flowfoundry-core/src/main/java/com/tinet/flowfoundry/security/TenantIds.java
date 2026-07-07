package com.tinet.flowfoundry.security;

import java.util.regex.Pattern;

/** Validates tenant identifiers used as workflow namespace keys. */
public final class TenantIds {

  private static final Pattern TENANT_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{1,64}$");

  private TenantIds() {}

  public static String normalize(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId is required");
    }
    String normalized = tenantId.trim();
    if (!TENANT_PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Invalid tenantId: " + tenantId);
    }
    return normalized;
  }
}
