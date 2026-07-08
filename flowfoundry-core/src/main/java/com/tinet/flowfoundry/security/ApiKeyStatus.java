package com.tinet.flowfoundry.security;

public final class ApiKeyStatus {

  public static final String ACTIVE = "active";
  public static final String DISABLED = "disabled";

  private ApiKeyStatus() {}

  public static String normalize(String status) {
    if (status == null || status.isBlank()) {
      return ACTIVE;
    }
    String normalized = status.trim().toLowerCase();
    if (!ACTIVE.equals(normalized) && !DISABLED.equals(normalized)) {
      throw new IllegalArgumentException("Unsupported API key status: " + status);
    }
    return normalized;
  }

  public static boolean isActive(String status) {
    return ACTIVE.equals(status);
  }
}
