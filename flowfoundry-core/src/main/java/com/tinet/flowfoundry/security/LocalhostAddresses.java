package com.tinet.flowfoundry.security;

public final class LocalhostAddresses {

  private LocalhostAddresses() {}

  public static boolean isLocalhost(String remoteAddr) {
    if (remoteAddr == null || remoteAddr.isBlank()) {
      return false;
    }
    String normalized = remoteAddr.trim();
    return "127.0.0.1".equals(normalized)
        || "::1".equals(normalized)
        || "0:0:0:0:0:0:0:1".equals(normalized);
  }
}
