package com.tinet.flowfoundry.security;

public final class PlatformSecurityHeaders {

  public static final String API_KEY = "X-Api-Key";
  /** Canonical namespace header: the single user-facing isolation unit. */
  public static final String PLATFORM_NAMESPACE = "X-Platform-Namespace";

  private PlatformSecurityHeaders() {}
}
