package com.tinet.flowfoundry.temporal;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryWorker;
import com.tinet.flowfoundry.config.NamespaceRoutingProperties;
import com.tinet.flowfoundry.config.TemporalProperties;
import com.tinet.flowfoundry.interpreter.FlowInterpreterWorkflowImpl;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 仅业务 Worker（{@code run-mode=worker}）启动 Temporal Worker。平台（flowfoundry-core）不运行业务
 * Worker，仅作为客户端按 namespace 启动 / 查询运行（docs/detailed-design.md §11）。
 *
 * <p>同一 Worker 二进制在两个 namespace 上各起一个 Worker（同 Task Queue、同解释器 + Activity 注册）：
 *
 * <ul>
 *   <li>业务 namespace（{@code temporal.namespace}）：承载生产运行，各使用方物理隔离；
 *   <li>系统 namespace（{@code flowfoundry.namespace.system}）：承载 flowfoundry-core 后台建模器发起的调试运行，
 *       与生产 run logs 隔离。调试运行使用桩 Activity，但桩化发生在 Activity 内部，故仍需 Worker 在该 namespace
 *       轮询同一 Task Queue。
 * </ul>
 */
@Component
@ConditionalOnFlowFoundryWorker
public class TemporalWorkerBootstrap {

  private static final Logger log = LoggerFactory.getLogger(TemporalWorkerBootstrap.class);

  private final TemporalProperties properties;
  private final TemporalClients temporalClients;
  private final NamespaceRoutingProperties namespaceRouting;
  private final List<TemporalWorkerExtension> extensions;

  private final List<WorkerFactory> factories = new ArrayList<>();

  public TemporalWorkerBootstrap(
      TemporalProperties properties,
      TemporalClients temporalClients,
      NamespaceRoutingProperties namespaceRouting,
      List<TemporalWorkerExtension> extensions) {
    this.properties = properties;
    this.temporalClients = temporalClients;
    this.namespaceRouting = namespaceRouting;
    this.extensions = extensions;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void startWorker() {
    String businessNamespace = properties.namespace();
    startWorkerFactory(businessNamespace);

    String systemNamespace = namespaceRouting.system();
    if (systemNamespace != null
        && !systemNamespace.isBlank()
        && !systemNamespace.equalsIgnoreCase(businessNamespace)) {
      startWorkerFactory(systemNamespace);
    }
  }

  private void startWorkerFactory(String namespace) {
    WorkerFactory factory =
        WorkerFactory.newInstance(temporalClients.workflowClient(namespace));
    WorkerOptions workerOptions =
        WorkerOptions.newBuilder()
            .setMaxConcurrentActivityExecutionSize(properties.maxConcurrentActivities())
            .setMaxConcurrentWorkflowTaskExecutionSize(properties.maxConcurrentWorkflows())
            .build();

    Worker worker = factory.newWorker(properties.taskQueue(), workerOptions);
    worker.registerWorkflowImplementationTypes(FlowInterpreterWorkflowImpl.class);
    for (TemporalWorkerExtension extension : extensions) {
      extension.register(worker);
    }

    factory.start();
    factories.add(factory);
    log.info(
        "Temporal worker started host={} namespace={} taskQueue={} extensions={}",
        properties.host(),
        namespace,
        properties.taskQueue(),
        extensions.size());
  }

  @PreDestroy
  public void shutdown() {
    for (WorkerFactory factory : factories) {
      factory.shutdown();
    }
    if (!factories.isEmpty()) {
      log.info("Temporal worker shutdown complete");
    }
  }
}
