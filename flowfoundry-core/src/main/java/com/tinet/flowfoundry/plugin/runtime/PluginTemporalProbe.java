package com.tinet.flowfoundry.plugin.runtime;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import com.tinet.flowfoundry.security.PlatformNamespaceEntity;
import com.tinet.flowfoundry.security.PlatformNamespaceRepository;
import com.tinet.flowfoundry.temporal.TemporalClusterBootstrapRunner;
import com.tinet.flowfoundry.temporal.TemporalConnectionRegistry;
import io.temporal.api.enums.v1.TaskQueueType;
import io.temporal.api.taskqueue.v1.TaskQueue;
import io.temporal.api.workflowservice.v1.DescribeTaskQueueRequest;
import io.temporal.api.workflowservice.v1.DescribeTaskQueueResponse;
import org.springframework.stereotype.Component;

/** Confirms plugin task queues have active pollers in Temporal. */
@Component
@ConditionalOnFlowFoundryPlatform
public class PluginTemporalProbe {

  private final TemporalConnectionRegistry connectionRegistry;
  private final PlatformNamespaceRepository namespaceRepository;

  public PluginTemporalProbe(
      TemporalConnectionRegistry connectionRegistry,
      PlatformNamespaceRepository namespaceRepository) {
    this.connectionRegistry = connectionRegistry;
    this.namespaceRepository = namespaceRepository;
  }

  public int activityPollers(String namespace, String taskQueue) {
    if (taskQueue == null || taskQueue.isBlank()) {
      return 0;
    }
    try {
      String clusterId = resolveClusterId(namespace);
      DescribeTaskQueueResponse response =
          connectionRegistry
              .clientsForCluster(clusterId)
              .serviceStubs()
              .blockingStub()
              .describeTaskQueue(
                  DescribeTaskQueueRequest.newBuilder()
                      .setNamespace(namespace)
                      .setTaskQueue(TaskQueue.newBuilder().setName(taskQueue).build())
                      .setTaskQueueType(TaskQueueType.TASK_QUEUE_TYPE_ACTIVITY)
                      .build());
      return response.getPollersCount();
    } catch (RuntimeException ignored) {
      return 0;
    }
  }

  private String resolveClusterId(String namespace) {
    return namespaceRepository
        .findById(namespace)
        .map(PlatformNamespaceEntity::getTemporalClusterId)
        .filter(id -> id != null && !id.isBlank())
        .orElse(TemporalClusterBootstrapRunner.DEFAULT_CLUSTER_ID);
  }
}
