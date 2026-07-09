package com.tinet.flowfoundry.temporal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinet.flowfoundry.registry.ActivityCatalogService;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed registry of app Worker deployment contracts ({@code namespace + taskQueue}).
 *
 * <p>When no Worker heartbeat exists, {@link #localContract()} falls back to the platform's
 * locally loaded business Activity Registry.
 */
@Component
public class DeploymentContractRegistry {

  private static final Logger log = LoggerFactory.getLogger(DeploymentContractRegistry.class);
  private static final String CONTRACT_PREFIX = "flowfoundry:contract:";
  private static final Duration CONTRACT_TTL = Duration.ofSeconds(90);

  private final StringRedisTemplate redis;
  private final ActivityCatalogService activityCatalog;
  private final ObjectMapper json = new ObjectMapper();

  public DeploymentContractRegistry(
      StringRedisTemplate redis, ActivityCatalogService activityCatalog) {
    this.redis = redis;
    this.activityCatalog = activityCatalog;
  }

  public DeploymentContract localContract() {
    String namespace = activityCatalog.localBusinessNamespace();
    String taskQueue = activityCatalog.localDefaultTaskQueue();
    return new DeploymentContract(namespace, namespace, taskQueue);
  }

  public void publish(DeploymentContract contract) {
    try {
      redis
          .opsForValue()
          .set(
              CONTRACT_PREFIX + contract.namespace(),
              json.writeValueAsString(contract),
              CONTRACT_TTL);
    } catch (Exception e) {
      log.warn("Failed to publish deployment contract {}: {}", contract, e.toString());
    }
  }

  public List<DeploymentContract> registeredContracts() {
    try {
      Set<String> keys = redis.keys(CONTRACT_PREFIX + "*");
      if (keys == null || keys.isEmpty()) {
        return List.of();
      }
      return keys.stream()
          .map(redis.opsForValue()::get)
          .filter(value -> value != null && !value.isBlank())
          .map(this::readContract)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .collect(Collectors.toList());
    } catch (Exception e) {
      log.warn("Failed to read deployment contracts from Redis: {}", e.toString());
      return List.of();
    }
  }

  /** Resolve Worker contract for a workflow / run namespace. */
  public DeploymentContract resolveForNamespace(String namespace) {
    if (namespace == null || namespace.isBlank()) {
      throw new IllegalArgumentException("namespace is required");
    }
    String normalized = namespace.trim();
    Optional<DeploymentContract> registered =
        registeredContracts().stream()
            .filter(contract -> normalized.equals(contract.namespace()))
            .findFirst();
    if (registered.isPresent()) {
      return registered.get();
    }
    if (normalized.equals(activityCatalog.localBusinessNamespace())) {
      return localContract();
    }
    throw new IllegalArgumentException(
        "No Worker registered for namespace: "
            + normalized
            + " (known: "
            + knownNamespaces()
            + ")");
  }

  /** All namespaces with a registered or local Worker contract. */
  public Set<String> knownNamespaces() {
    Set<String> namespaces = new LinkedHashSet<>();
    namespaces.add(activityCatalog.localBusinessNamespace());
    for (DeploymentContract contract : registeredContracts()) {
      if (contract.namespace() != null && !contract.namespace().isBlank()) {
        namespaces.add(contract.namespace());
      }
    }
    return namespaces;
  }

  private Optional<DeploymentContract> readContract(String value) {
    try {
      JsonNode node = json.readTree(value);
      String appId = textOrNull(node.get("appId"));
      String namespace =
          firstNonBlank(
              textOrNull(node.get("namespace")),
              textOrNull(node.get("registryNamespace")),
              textOrNull(node.get("temporalNamespace")));
      String taskQueue = textOrNull(node.get("taskQueue"));
      if (namespace == null || taskQueue == null) {
        return Optional.empty();
      }
      if (appId == null || appId.isBlank()) {
        appId = namespace;
      }
      return Optional.of(new DeploymentContract(appId, namespace, taskQueue));
    } catch (Exception e) {
      log.warn("Ignoring malformed deployment contract payload: {}", e.toString());
      return Optional.empty();
    }
  }

  private static String textOrNull(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    String value = node.asText();
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return null;
  }
}
