package com.tinet.flowfoundry.temporal;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryWorker;
import com.tinet.flowfoundry.registry.ActivityRegistry;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 业务 Worker（{@code run-mode=worker}）启动后向平台注册自身部署契约，并周期性续期心跳。
 *
 * <p>平台据此把该使用方的 workflow 执行 / run logs 路由到其独立的 Temporal namespace（docs/detailed-design.md
 * §11.4）。使用共享 Redis 传输，避免 Worker 反向依赖平台地址与启动顺序。
 */
@Component
@ConditionalOnFlowFoundryWorker
public class WorkerContractPublisher {

  private static final Logger log = LoggerFactory.getLogger(WorkerContractPublisher.class);
  private static final long HEARTBEAT_SECONDS = 30;

  private final DeploymentContractRegistry contractRegistry;
  private final ActivityRegistry activityRegistry;
  private final String appId;

  private ScheduledExecutorService scheduler;

  public WorkerContractPublisher(
      DeploymentContractRegistry contractRegistry,
      ActivityRegistry activityRegistry,
      @Value("${spring.application.name:flowfoundry-worker}") String appId) {
    this.contractRegistry = contractRegistry;
    this.activityRegistry = activityRegistry;
    this.appId = appId;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void start() {
    DeploymentContract contract = selfContract();
    contractRegistry.publish(contract);
    log.info(
        "Published deployment contract appId={} namespace={} taskQueue={}",
        contract.appId(),
        contract.namespace(),
        contract.taskQueue());
    scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread thread = new Thread(r, "flowfoundry-contract-heartbeat");
      thread.setDaemon(true);
      return thread;
    });
    scheduler.scheduleAtFixedRate(
        () -> contractRegistry.publish(selfContract()),
        HEARTBEAT_SECONDS,
        HEARTBEAT_SECONDS,
        TimeUnit.SECONDS);
  }

  private DeploymentContract selfContract() {
    return new DeploymentContract(
        appId,
        activityRegistry.namespace(),
        activityRegistry.defaultTaskQueue());
  }

  @PreDestroy
  public void shutdown() {
    if (scheduler != null) {
      scheduler.shutdownNow();
    }
  }
}
