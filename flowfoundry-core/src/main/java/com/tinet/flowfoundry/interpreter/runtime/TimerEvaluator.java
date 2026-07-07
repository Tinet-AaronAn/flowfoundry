package com.tinet.flowfoundry.interpreter.runtime;

import com.tinet.flowfoundry.interpreter.model.ExecutionNode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves intermediate timer delays from node config and workflow variables. */
public final class TimerEvaluator {

  private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

  public enum PastTargetStrategy {
    FIRE_IMMEDIATELY,
    SKIP,
    FAIL;

    static PastTargetStrategy from(Object raw) {
      if (raw == null || String.valueOf(raw).isBlank()) {
        return FIRE_IMMEDIATELY;
      }
      String value = String.valueOf(raw).trim();
      for (PastTargetStrategy strategy : values()) {
        if (strategy.name().equalsIgnoreCase(value.replace("-", "_"))) {
          return strategy;
        }
      }
      return switch (value.toLowerCase()) {
        case "fireimmediately", "fire_immediately" -> FIRE_IMMEDIATELY;
        case "skip" -> SKIP;
        case "fail" -> FAIL;
        default -> FIRE_IMMEDIATELY;
      };
    }
  }

  public record TimerDelay(long delayMs) {
    public static TimerDelay immediate() {
      return new TimerDelay(0L);
    }
  }

  private TimerEvaluator() {}

  public static TimerDelay evaluate(ExecutionNode node, VariableStore variables, long nowEpochMs) {
    TimerDefinition definition = TimerDefinition.from(node);
    if (definition == null) {
      return TimerDelay.immediate();
    }
    Object resolvedValue = resolveValue(definition.rawValue(), variables);
    return switch (definition.type()) {
      case "date" -> evaluateDate(definition, resolvedValue, variables, nowEpochMs, node.id());
      case "cycle" -> throw new UnsupportedOperationException(
          "Timer type 'cycle' is not yet supported at runtime: " + node.id());
      default -> new TimerDelay(parseDurationMs(stringify(resolvedValue)));
    };
  }

  private static TimerDelay evaluateDate(
      TimerDefinition definition,
      Object resolvedValue,
      VariableStore variables,
      long nowEpochMs,
      String nodeId) {
    if (resolvedValue == null || stringify(resolvedValue).isBlank()) {
      throw new IllegalArgumentException("Timer date value is required: " + nodeId);
    }
    long targetEpochMs = parseTargetEpochMs(resolvedValue, definition.timezone(), variables, nodeId);
    long delayMs = targetEpochMs - nowEpochMs;
    if (delayMs > 0) {
      return new TimerDelay(delayMs);
    }
    return applyPastTargetStrategy(definition.pastTargetStrategy(), nodeId, targetEpochMs, nowEpochMs);
  }

  private static TimerDelay applyPastTargetStrategy(
      PastTargetStrategy strategy, String nodeId, long targetEpochMs, long nowEpochMs) {
    return switch (strategy) {
      case FIRE_IMMEDIATELY, SKIP -> TimerDelay.immediate();
      case FAIL ->
          throw new IllegalStateException(
              "Timer date is in the past for node "
                  + nodeId
                  + " (target="
                  + Instant.ofEpochMilli(targetEpochMs)
                  + ", now="
                  + Instant.ofEpochMilli(nowEpochMs)
                  + ")");
    };
  }

  private static long parseTargetEpochMs(
      Object resolvedValue, String timezone, VariableStore variables, String nodeId) {
    if (resolvedValue instanceof Number number) {
      return number.longValue();
    }
    String text = stringify(resolvedValue).trim();
    if (text.matches("-?\\d+")) {
      return Long.parseLong(text);
    }
    try {
      return Instant.parse(text).toEpochMilli();
    } catch (DateTimeParseException ignored) {
      // Try local date-time with optional timezone.
    }
    ZoneId zone = resolveZoneId(timezone, variables);
    try {
      LocalDateTime localDateTime = LocalDateTime.parse(text);
      return localDateTime.atZone(zone).toInstant().toEpochMilli();
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException(
          "Invalid timer date value for node " + nodeId + ": " + text, e);
    }
  }

  private static ZoneId resolveZoneId(String timezone, VariableStore variables) {
    String resolved = resolveText(timezone, variables);
    if (resolved == null || resolved.isBlank()) {
      return ZoneId.systemDefault();
    }
    return ZoneId.of(resolved.trim());
  }

  static Object resolveValue(String raw, VariableStore variables) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String text = raw.trim();
    if (!text.contains("${")) {
      return text;
    }
    Matcher matcher = VARIABLE_PATTERN.matcher(text);
    if (matcher.matches()) {
      return variables.resolve(matcher.group(1));
    }
    StringBuffer buffer = new StringBuffer();
    matcher.reset();
    while (matcher.find()) {
      Object resolved = variables.resolve(matcher.group(1));
      matcher.appendReplacement(buffer, Matcher.quoteReplacement(stringify(resolved)));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  static String resolveText(String raw, VariableStore variables) {
    Object resolved = resolveValue(raw, variables);
    return resolved == null ? null : stringify(resolved);
  }

  static long parseDurationMs(String raw) {
    if (raw == null || raw.isBlank()) {
      return 0L;
    }
    String value = raw.trim();
    String lower = value.toLowerCase();
    if (lower.endsWith("ms")) {
      return Long.parseLong(lower.substring(0, lower.length() - 2));
    }
    if (lower.endsWith("s") && !lower.startsWith("pt")) {
      return Long.parseLong(lower.substring(0, lower.length() - 1)) * 1000L;
    }
    if (lower.endsWith("m") && !lower.startsWith("pt")) {
      return Long.parseLong(lower.substring(0, lower.length() - 1)) * 60_000L;
    }
    if (lower.endsWith("h") && !lower.startsWith("pt")) {
      return Long.parseLong(lower.substring(0, lower.length() - 1)) * 3_600_000L;
    }
    try {
      return java.time.Duration.parse(value).toMillis();
    } catch (DateTimeParseException e) {
      return 0L;
    }
  }

  private static String stringify(Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof ZonedDateTime zonedDateTime) {
      return zonedDateTime.toInstant().toString();
    }
    if (value instanceof Instant instant) {
      return instant.toString();
    }
    return String.valueOf(value);
  }

  private record TimerDefinition(
      String type, String rawValue, String timezone, PastTargetStrategy pastTargetStrategy) {

    static TimerDefinition from(ExecutionNode node) {
      if (node.config() == null || node.config().isEmpty()) {
        return null;
      }
      Object timerDefinition = node.config().get("timerDefinition");
      if (timerDefinition instanceof Map<?, ?> map) {
        String type =
            map.get("type") == null ? "duration" : String.valueOf(map.get("type")).trim().toLowerCase();
        String value = map.get("value") == null ? null : String.valueOf(map.get("value"));
        String timezone = map.get("timezone") == null ? null : String.valueOf(map.get("timezone"));
        PastTargetStrategy strategy = PastTargetStrategy.from(map.get("pastTargetStrategy"));
        if (value != null && !value.isBlank()) {
          return new TimerDefinition(type, value, timezone, strategy);
        }
      }
      Object duration = node.config().get("duration");
      if (duration == null) {
        return null;
      }
      return new TimerDefinition("duration", String.valueOf(duration), null, PastTargetStrategy.FIRE_IMMEDIATELY);
    }
  }
}
