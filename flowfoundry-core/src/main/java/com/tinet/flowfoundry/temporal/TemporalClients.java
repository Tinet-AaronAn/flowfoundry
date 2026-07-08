package com.tinet.flowfoundry.temporal;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 Temporal namespace 缓存的 {@link WorkflowClient} / {@link ScheduleClient} 工厂。
 *
 * <p>取代原先绑死单一 namespace 的单例客户端：平台需要同时与「系统 namespace」及各使用方业务 namespace
 * 通信，因此按 namespace 懒加载并复用客户端，共享同一个 {@link WorkflowServiceStubs}（同一 Temporal 集群）。
 */
public class TemporalClients {

  private final WorkflowServiceStubs serviceStubs;
  private final Map<String, WorkflowClient> workflowClients = new ConcurrentHashMap<>();
  private final Map<String, ScheduleClient> scheduleClients = new ConcurrentHashMap<>();

  public TemporalClients(WorkflowServiceStubs serviceStubs) {
    this.serviceStubs = serviceStubs;
  }

  public WorkflowServiceStubs serviceStubs() {
    return serviceStubs;
  }

  public WorkflowClient workflowClient(String namespace) {
    return workflowClients.computeIfAbsent(
        requireNamespace(namespace),
        ns ->
            WorkflowClient.newInstance(
                serviceStubs, WorkflowClientOptions.newBuilder().setNamespace(ns).build()));
  }

  public ScheduleClient scheduleClient(String namespace) {
    return scheduleClients.computeIfAbsent(
        requireNamespace(namespace),
        ns ->
            ScheduleClient.newInstance(
                serviceStubs, ScheduleClientOptions.newBuilder().setNamespace(ns).build()));
  }

  private static String requireNamespace(String namespace) {
    if (namespace == null || namespace.isBlank()) {
      throw new IllegalArgumentException("Temporal namespace must not be blank");
    }
    return namespace.trim();
  }
}
