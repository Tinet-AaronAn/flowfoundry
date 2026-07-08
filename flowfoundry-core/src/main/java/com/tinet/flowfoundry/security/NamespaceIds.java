package com.tinet.flowfoundry.security;

import java.util.regex.Pattern;

/** Validates namespace identifiers (workflow / run logs / api-key scope key). */
public final class NamespaceIds {

  private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{1,64}$");

  private NamespaceIds() {}

  public static String normalize(String namespace) {
    if (namespace == null || namespace.isBlank()) {
      throw new IllegalArgumentException("namespace is required");
    }
    String normalized = namespace.trim();
    if (!NAMESPACE_PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Invalid namespace: " + namespace);
    }
    return normalized;
  }
}
