package com.tinet.flowfoundry.security;

import java.security.SecureRandom;

public final class ApiKeyGenerator {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final char[] HEX = "0123456789abcdef".toCharArray();

  private ApiKeyGenerator() {}

  public static String generate() {
    byte[] bytes = new byte[24];
    RANDOM.nextBytes(bytes);
    char[] encoded = new char[bytes.length * 2];
    for (int i = 0; i < bytes.length; i++) {
      int value = bytes[i] & 0xff;
      encoded[i * 2] = HEX[value >>> 4];
      encoded[i * 2 + 1] = HEX[value & 0x0f];
    }
    return "ffk_" + new String(encoded);
  }

  public static String prefix(String rawKey) {
    if (rawKey == null || rawKey.isBlank()) {
      return "";
    }
    String trimmed = rawKey.trim();
    if (trimmed.length() <= 12) {
      return trimmed;
    }
    return trimmed.substring(0, 12) + "...";
  }
}
