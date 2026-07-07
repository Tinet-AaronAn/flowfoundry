package com.tinet.flowfoundry.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ActivityRegistry(
    String version,
    String namespace,
    String defaultTaskQueue,
    List<ActivityGroup> groups,
    List<ActivityDefinition> activities) {

  public ActivityRegistry {
    groups = groups == null ? List.of() : List.copyOf(groups);
    activities = activities == null ? List.of() : List.copyOf(activities);
  }

  public ActivityRegistry(
      String version, String namespace, String defaultTaskQueue, List<ActivityDefinition> activities) {
    this(version, namespace, defaultTaskQueue, List.of(), activities);
  }

  private Map<String, ActivityDefinition> index() {
    return activities.stream()
        .collect(Collectors.toMap(ActivityDefinition::id, Function.identity()));
  }

  public Optional<ActivityDefinition> find(String activityId) {
    return Optional.ofNullable(index().get(activityId));
  }

  public ActivityDefinition require(String activityId) {
    return find(activityId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown activity: " + activityId));
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ActivityGroup(String id, String name, String description, Integer order) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ActivityDefinition(
      String id,
      String name,
      String description,
      String group,
      String taskQueue,
      String timeout,
      RetryPolicy retry,
      IdempotencyPolicy idempotency,
      List<ParameterSpec> input,
      List<ParameterSpec> output,
      Boolean cancellable) {

    public ActivityDefinition(
        String id,
        String name,
        String description,
        String taskQueue,
        String timeout,
        RetryPolicy retry,
        IdempotencyPolicy idempotency,
        List<ParameterSpec> input,
        List<ParameterSpec> output) {
      this(
          id,
          name,
          description,
          null,
          taskQueue,
          timeout,
          retry,
          idempotency,
          input,
          output,
          null);
    }

    public ActivityDefinition(
        String id,
        String name,
        String description,
        String taskQueue,
        String timeout,
        RetryPolicy retry,
        IdempotencyPolicy idempotency,
        List<ParameterSpec> input,
        List<ParameterSpec> output,
        Boolean cancellable) {
      this(
          id,
          name,
          description,
          null,
          taskQueue,
          timeout,
          retry,
          idempotency,
          input,
          output,
          cancellable);
    }

    public String resolvedGroup() {
      return group == null || group.isBlank() ? "default" : group.trim();
    }

    public boolean isCancellable() {
      return Boolean.TRUE.equals(cancellable);
    }

    public Duration timeoutDuration() {
      return parseDuration(timeout, Duration.ofMinutes(1));
    }

    public Duration idempotencyTtl() {
      if (idempotency == null || idempotency.ttl() == null) {
        return Duration.ofHours(24);
      }
      return parseDuration(idempotency.ttl(), Duration.ofHours(24));
    }

    public int maxAttempts() {
      return retry != null && retry.maximumAttempts() != null ? retry.maximumAttempts() : 3;
    }

    private static Duration parseDuration(String raw, Duration fallback) {
      if (raw == null || raw.isBlank()) {
        return fallback;
      }
      String value = raw.trim().toLowerCase();
      if (value.endsWith("ms")) {
        return Duration.ofMillis(Long.parseLong(value.replace("ms", "")));
      }
      if (value.endsWith("s")) {
        return Duration.ofSeconds(Long.parseLong(value.replace("s", "")));
      }
      if (value.endsWith("m")) {
        return Duration.ofMinutes(Long.parseLong(value.replace("m", "")));
      }
      if (value.endsWith("h")) {
        return Duration.ofHours(Long.parseLong(value.replace("h", "")));
      }
      return Duration.parse("PT" + raw.toUpperCase());
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record RetryPolicy(
      String initialInterval,
      Double backoffCoefficient,
      Integer maximumAttempts,
      List<String> nonRetryableErrors) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record IdempotencyPolicy(String keyPattern, String ttl) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ParameterSpec(String name, String type, Boolean required, String defaultValue) {}
}
