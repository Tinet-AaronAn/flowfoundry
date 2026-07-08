package com.tinet.flowfoundry.temporal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinet.flowfoundry.config.NamespaceRoutingProperties;
import com.tinet.flowfoundry.config.TemporalProperties;
import com.tinet.flowfoundry.registry.ActivityRegistry;
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
 * 使用方部署契约注册表（Redis 共享）。
 *
 * <p>Worker 启动时 {@link #publish(DeploymentContract)} 上报自身 {@code {namespace, taskQueue}}；平台据此把
 * workflow 执行 / run logs 路由到该使用方独立的 Temporal namespace（详见 docs/detailed-design.md §11）。
 *
 * <p>当尚无 Worker 注册时，平台回退到 {@link #localContract()}（由本地加载的 Activity 注册表 + {@code
 * temporal.namespace} 推导），保证单场景部署开箱可用。
 */
@Component
public class DeploymentContractRegistry {

  private static final Logger log = LoggerFactory.getLogger(DeploymentContractRegistry.class);
  private static final String CONTRACT_PREFIX = "flowfoundry:contract:";
  private static final Duration CONTRACT_TTL = Duration.ofSeconds(90);

  private final StringRedisTemplate redis;
  private final TemporalProperties temporalProperties;
  private final ActivityRegistry activityRegistry;
  private final NamespaceRoutingProperties namespaceRouting;
  private final ObjectMapper json = new ObjectMapper();

  public DeploymentContractRegistry(
      StringRedisTemplate redis,
      TemporalProperties temporalProperties,
      ActivityRegistry activityRegistry,
      NamespaceRoutingProperties namespaceRouting) {
    this.redis = redis;
    this.temporalProperties = temporalProperties;
    this.activityRegistry = activityRegistry;
    this.namespaceRouting = namespaceRouting;
  }

  public String systemNamespace() {
    return namespaceRouting.system();
  }

  /**
   * 平台回退契约：无 Worker 注册时据本地 Activity 注册表 + {@code temporal.namespace} 推导。业务 Task Queue
   * 取注册表 {@code defaultTaskQueue}（使用方 Worker 实际轮询的队列），而非平台自身的 {@code
   * temporal.task-queue}。
   */
  public DeploymentContract localContract() {
    String registryNamespace = activityRegistry.namespace();
    String taskQueue =
        firstNonBlank(activityRegistry.defaultTaskQueue(), temporalProperties.taskQueue());
    return new DeploymentContract(
        registryNamespace, registryNamespace, temporalProperties.namespace(), taskQueue);
  }

  public void publish(DeploymentContract contract) {
    try {
      redis
          .opsForValue()
          .set(
              CONTRACT_PREFIX + contract.registryNamespace(),
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

  /** 为一次运行解析目标契约：优先已注册的同 registryNamespace 契约，否则回退本地契约。 */
  public DeploymentContract resolveForRun() {
    String registryNamespace = activityRegistry.namespace();
    return registeredContracts().stream()
        .filter(c -> registryNamespace.equals(c.registryNamespace()))
        .findFirst()
        .orElseGet(this::localContract);
  }

  /** 所有已知业务 Temporal namespace（本地契约 + 已注册契约），用于 run→namespace 反查扫描。 */
  public Set<String> businessNamespaces() {
    Set<String> namespaces = new LinkedHashSet<>();
    namespaces.add(localContract().temporalNamespace());
    for (DeploymentContract contract : registeredContracts()) {
      if (contract.temporalNamespace() != null && !contract.temporalNamespace().isBlank()) {
        namespaces.add(contract.temporalNamespace());
      }
    }
    return namespaces;
  }

  private Optional<DeploymentContract> readContract(String value) {
    try {
      return Optional.of(json.readValue(value, DeploymentContract.class));
    } catch (Exception e) {
      log.warn("Ignoring malformed deployment contract payload: {}", e.toString());
      return Optional.empty();
    }
  }

  private static String firstNonBlank(String primary, String fallback) {
    if (primary != null && !primary.isBlank()) {
      return primary;
    }
    return fallback;
  }
}
