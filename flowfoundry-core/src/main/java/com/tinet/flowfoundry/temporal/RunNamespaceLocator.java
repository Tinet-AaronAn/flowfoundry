package com.tinet.flowfoundry.temporal;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 反查一次运行（workflowId）所属的 Temporal 业务 namespace。
 *
 * <p>启动运行时 {@link #remember(String, String)} 记录 {@code workflowId -> namespace}；查询运行状态 / run
 * logs 时 {@link #locate(String)} 先读缓存，未命中则跨已知业务 namespace 扫描（如定时调度触发的运行），
 * 命中后回填缓存。取代此前无脑使用全局 {@code temporal.namespace} 的做法。
 */
@Component
@ConditionalOnFlowFoundryPlatform
public class RunNamespaceLocator {

  private static final Logger log = LoggerFactory.getLogger(RunNamespaceLocator.class);
  private static final String RUN_NS_PREFIX = "flowfoundry:run-ns:";
  private static final Duration RUN_NS_TTL = Duration.ofDays(30);

  private final StringRedisTemplate redis;
  private final TemporalClients temporalClients;
  private final DeploymentContractRegistry contractRegistry;

  public RunNamespaceLocator(
      StringRedisTemplate redis,
      TemporalClients temporalClients,
      DeploymentContractRegistry contractRegistry) {
    this.redis = redis;
    this.temporalClients = temporalClients;
    this.contractRegistry = contractRegistry;
  }

  public void remember(String workflowId, String namespace) {
    if (workflowId == null || workflowId.isBlank() || namespace == null || namespace.isBlank()) {
      return;
    }
    try {
      redis.opsForValue().set(RUN_NS_PREFIX + workflowId, namespace, RUN_NS_TTL);
    } catch (Exception e) {
      log.warn("Failed to record run namespace for {}: {}", workflowId, e.toString());
    }
  }

  public String locate(String workflowId) {
    String cached = readCached(workflowId);
    if (cached != null && !cached.isBlank()) {
      return cached;
    }
    String scanned = scanKnownNamespaces(workflowId);
    if (scanned != null) {
      remember(workflowId, scanned);
      return scanned;
    }
    return contractRegistry.localContract().namespace();
  }

  private String readCached(String workflowId) {
    try {
      return redis.opsForValue().get(RUN_NS_PREFIX + workflowId);
    } catch (Exception e) {
      return null;
    }
  }

  private String scanKnownNamespaces(String workflowId) {
    WorkflowExecution execution =
        WorkflowExecution.newBuilder().setWorkflowId(workflowId).build();
    for (String namespace : contractRegistry.knownNamespaces()) {
      if (existsIn(namespace, execution)) {
        return namespace;
      }
    }
    return null;
  }

  private boolean existsIn(String namespace, WorkflowExecution execution) {
    try {
      temporalClients
          .serviceStubs()
          .blockingStub()
          .describeWorkflowExecution(
              DescribeWorkflowExecutionRequest.newBuilder()
                  .setNamespace(namespace)
                  .setExecution(execution)
                  .build());
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
