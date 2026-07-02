package com.example.platform.workflow;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class VersionNumbering {

  private VersionNumbering() {}

  public static final String INITIAL_VERSION = "1.0.0";

  public static String nextVersion(String current) {
    List<Integer> parts = parse(current);
    parts.set(2, parts.get(2) + 1);
    return format(parts);
  }

  public static String ensureValid(String version) {
    parse(version);
    return version.trim();
  }

  private static List<Integer> parse(String version) {
    if (version == null || version.isBlank()) {
      throw new IllegalArgumentException("version is required");
    }
    String[] tokens = version.trim().split("\\.");
    if (tokens.length != 3) {
      throw new IllegalArgumentException("version must use major.minor.patch format");
    }
    try {
      return Arrays.stream(tokens).map(Integer::parseInt).collect(Collectors.toList());
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("version must use numeric major.minor.patch format", ex);
    }
  }

  private static String format(List<Integer> parts) {
    return parts.get(0) + "." + parts.get(1) + "." + parts.get(2);
  }
}
