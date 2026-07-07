package com.tinet.flowfoundry.security;

public final class PlatformSecurityHeaders {

  public static final String API_KEY = "X-Api-Key";
  public static final String PLATFORM_NAMESPACE = "X-Platform-Namespace";
  /** Tenant identifier; workflow namespace equals tenantId in the multi-tenant model. */
  public static final String TENANT_ID = "X-Tenant-Id";

  private PlatformSecurityHeaders() {}
}
