package com.tinet.flowfoundry.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ApiKeyHasher {

  private ApiKeyHasher() {}

  public static String hash(String rawKey) {
    if (rawKey == null || rawKey.isBlank()) {
      throw new IllegalArgumentException("API key is required");
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(rawKey.trim().getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(hashed.length * 2);
      for (byte value : hashed) {
        builder.append(String.format("%02x", value));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 not available", ex);
    }
  }
}
