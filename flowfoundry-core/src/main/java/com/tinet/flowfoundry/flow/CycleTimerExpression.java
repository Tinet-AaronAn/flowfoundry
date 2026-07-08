package com.tinet.flowfoundry.flow;

import com.tinet.flowfoundry.interpreter.runtime.TimerEvaluator;
import java.time.format.DateTimeParseException;

/** Parses ISO-8601 repeating interval expressions (BPMN timeCycle), e.g. {@code R/PT1H}. */
public final class CycleTimerExpression {

  public record Parsed(Integer repeatCount, String startText, long intervalMs) {}

  private CycleTimerExpression() {}

  public static Parsed parse(String raw, String contextId) {
    String text = raw.trim();
    if (!text.regionMatches(true, 0, "R", 0, 1)) {
      throw new IllegalArgumentException("Invalid timer cycle value for " + contextId + ": " + raw);
    }
    String body = text.substring(1);
    if (body.startsWith("/")) {
      body = body.substring(1);
    }
    if (body.isBlank()) {
      throw new IllegalArgumentException("Timer cycle value is required for " + contextId);
    }
    String[] segments = body.split("/");
    if (segments.length == 0 || segments[segments.length - 1].isBlank()) {
      throw new IllegalArgumentException("Invalid timer cycle value for " + contextId + ": " + raw);
    }
    int index = 0;
    Integer repeatCount = null;
    if (segments.length > 1 && segments[0].matches("\\d+")) {
      repeatCount = Integer.parseInt(segments[0]);
      index = 1;
    }
    String intervalPart = segments[segments.length - 1].trim();
    if (!isDurationToken(intervalPart)) {
      throw new IllegalArgumentException(
          "Timer cycle interval must be an ISO-8601 duration for "
              + contextId
              + ": "
              + intervalPart);
    }
    long intervalMs = TimerEvaluator.parseDurationMs(intervalPart);
    String startText = null;
    if (segments.length - index > 1) {
      startText = segments[index].trim();
      if (startText.isBlank()) {
        throw new IllegalArgumentException("Invalid timer cycle start for " + contextId + ": " + raw);
      }
    }
    return new Parsed(repeatCount, startText, intervalMs);
  }

  private static boolean isDurationToken(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    String lower = value.trim().toLowerCase();
    if (lower.endsWith("ms") || lower.matches("\\d+[smh]")) {
      return true;
    }
    return lower.startsWith("p") || lower.startsWith("pt");
  }

  public static void rejectVariables(String value, String contextId) {
    if (value != null && value.contains("${")) {
      throw new IllegalArgumentException(
          "Timer Start schedule requires a literal cycle/date value (no ${...} variables): "
              + contextId);
    }
  }

  public static java.time.Duration intervalDuration(String raw, String contextId) {
    Parsed parsed = parse(raw, contextId);
    if (parsed.intervalMs() <= 0) {
      throw new IllegalArgumentException("Timer cycle interval must be positive for " + contextId);
    }
    return java.time.Duration.ofMillis(parsed.intervalMs());
  }

  public static java.time.Instant parseStartInstant(
      String startText, String timezone, String contextId) {
    if (startText == null || startText.isBlank()) {
      return null;
    }
    try {
      return java.time.Instant.parse(startText.trim());
    } catch (DateTimeParseException ignored) {
      java.time.ZoneId zone =
          timezone == null || timezone.isBlank()
              ? java.time.ZoneId.systemDefault()
              : java.time.ZoneId.of(timezone.trim());
      try {
        return java.time.LocalDateTime.parse(startText.trim()).atZone(zone).toInstant();
      } catch (DateTimeParseException e) {
        throw new IllegalArgumentException(
            "Invalid timer cycle start for " + contextId + ": " + startText, e);
      }
    }
  }
}
