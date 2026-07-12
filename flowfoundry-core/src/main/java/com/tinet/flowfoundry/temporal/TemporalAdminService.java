package com.tinet.flowfoundry.temporal;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import com.tinet.flowfoundry.config.TemporalProperties;
import com.tinet.flowfoundry.security.AdminAccessService;
import com.tinet.flowfoundry.security.AuditActions;
import com.tinet.flowfoundry.security.AuditLogService;
import com.tinet.flowfoundry.security.NamespaceAdminService;
import com.tinet.flowfoundry.security.PlatformNamespaceEntity;
import com.tinet.flowfoundry.security.PlatformNamespaceRepository;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.TemporalNamespaceAlignmentDto;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.TemporalNamespaceAlignmentPageDto;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.TemporalOverviewDto;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.TemporalPlatformQueueDto;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.TemporalScheduleDto;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.TemporalSchedulePageDto;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.TemporalSummaryCountsDto;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.TemporalClusterSummaryDto;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.TemporalWorkerRowDto;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.TemporalWorkerPageDto;
import com.tinet.flowfoundry.temporal.TemporalAdminContracts.TemporalWorkerStatusDto;
import io.temporal.api.enums.v1.TaskQueueType;
import io.temporal.api.taskqueue.v1.TaskQueue;
import io.temporal.api.workflowservice.v1.DescribeNamespaceRequest;
import io.temporal.api.workflowservice.v1.DescribeNamespaceResponse;
import io.temporal.api.workflowservice.v1.DescribeTaskQueueRequest;
import io.temporal.api.workflowservice.v1.DescribeTaskQueueResponse;
import io.temporal.api.workflowservice.v1.ListNamespacesRequest;
import io.temporal.api.workflowservice.v1.ListNamespacesResponse;
import io.temporal.api.workflowservice.v1.RegisterNamespaceRequest;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleListDescription;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnFlowFoundryPlatform
public class TemporalAdminService {

  private static final Logger log = LoggerFactory.getLogger(TemporalAdminService.class);
  private static final String CONTRACT_PREFIX = "flowfoundry:contract:";
  private static final String PLATFORM_TASK_QUEUE = "flowfoundry-platform";

  private final PlatformNamespaceRepository namespaceRepository;
  private final TemporalClusterRepository clusterRepository;
  private final TemporalConnectionRegistry connectionRegistry;
  private final TemporalClusterProbe clusterProbe;
  private final DeploymentContractRegistry contractRegistry;
  private final StringRedisTemplate redis;
  private final TemporalProperties properties;
  private final AdminAccessService adminAccessService;
  private final AuditLogService auditLogService;
  private final Map<String, CachedTaskQueueStats> taskQueueCache = new ConcurrentHashMap<>();

  public TemporalAdminService(
      PlatformNamespaceRepository namespaceRepository,
      TemporalClusterRepository clusterRepository,
      TemporalConnectionRegistry connectionRegistry,
      TemporalClusterProbe clusterProbe,
      DeploymentContractRegistry contractRegistry,
      StringRedisTemplate redis,
      TemporalProperties properties,
      AdminAccessService adminAccessService,
      AuditLogService auditLogService) {
    this.namespaceRepository = namespaceRepository;
    this.clusterRepository = clusterRepository;
    this.connectionRegistry = connectionRegistry;
    this.clusterProbe = clusterProbe;
    this.contractRegistry = contractRegistry;
    this.redis = redis;
    this.properties = properties;
    this.adminAccessService = adminAccessService;
    this.auditLogService = auditLogService;
  }

  @Transactional(readOnly = true)
  public TemporalOverviewDto overview() {
    adminAccessService.requireAdmin();
    TemporalNamespaceAlignmentPageDto alignment = namespaceAlignment(false);
    TemporalSummaryCountsDto summary = summarize(alignment.items());
    TemporalClusterEntity defaultCluster = requireDefaultCluster();
    TemporalClusterProbe.ProbeResult probe = clusterProbe.probe(defaultCluster.getId());
    TemporalClusterSummaryDto cluster =
        new TemporalClusterSummaryDto(
            defaultCluster.getId(),
            defaultCluster.getDisplayName(),
            defaultCluster.getHost(),
            TemporalClusterAdminService.resolvedUiBaseUrl(defaultCluster),
            probe.reachable(),
            probe.serverVersion(),
            Instant.now());
    return new TemporalOverviewDto(cluster, summary);
  }

  @Transactional(readOnly = true)
  public TemporalNamespaceAlignmentPageDto namespaceAlignment(boolean refreshTaskQueues) {
    adminAccessService.requireAdmin();
    if (refreshTaskQueues) {
      taskQueueCache.clear();
    }
    Map<String, Set<String>> temporalByCluster = loadTemporalNamespacesByCluster();
    Map<String, DeploymentContract> contracts = contractsByNamespace();
    List<TemporalNamespaceAlignmentDto> items = new ArrayList<>();
    Set<String> platformIds = new LinkedHashSet<>();

    for (PlatformNamespaceEntity entity :
        namespaceRepository.findAll().stream()
            .sorted(Comparator.comparing(PlatformNamespaceEntity::getId))
            .toList()) {
      platformIds.add(entity.getId());
      String clusterId = connectionRegistry.resolveClusterId(entity.getId());
      TemporalClusterEntity cluster = requireCluster(clusterId);
      boolean temporalRegistered =
          temporalByCluster.getOrDefault(clusterId, Set.of()).contains(entity.getId());
      DeploymentContract contract = contracts.get(entity.getId());
      TemporalWorkerStatusDto worker = toWorkerStatus(contract, entity.getId());
      int pollers = pollCount(clusterId, entity.getId(), contract);
      String status = resolveStatus(temporalRegistered, worker);
      items.add(
          new TemporalNamespaceAlignmentDto(
              entity.getId(),
              entity.getDisplayName(),
              clusterId,
              cluster.getDisplayName(),
              true,
              temporalRegistered,
              worker,
              pollers,
              status,
              buildNamespaceUiUrl(cluster, entity.getId())));
    }

    for (Map.Entry<String, Set<String>> entry : temporalByCluster.entrySet()) {
      String clusterId = entry.getKey();
      TemporalClusterEntity cluster = requireCluster(clusterId);
      for (String temporalNamespace : entry.getValue()) {
        if (platformIds.contains(temporalNamespace)) {
          continue;
        }
        items.add(
            new TemporalNamespaceAlignmentDto(
                temporalNamespace,
                temporalNamespace,
                clusterId,
                cluster.getDisplayName(),
                false,
                true,
                null,
                0,
                "ORPHAN_TEMPORAL",
                buildNamespaceUiUrl(cluster, temporalNamespace)));
      }
    }

    items.sort(Comparator.comparing(TemporalNamespaceAlignmentDto::id));
    return new TemporalNamespaceAlignmentPageDto(items);
  }

  @Transactional
  public TemporalNamespaceAlignmentDto registerTemporalNamespace(String namespaceId) {
    adminAccessService.requireAdmin();
    PlatformNamespaceEntity entity =
        namespaceRepository
            .findById(namespaceId.trim())
            .orElseThrow(
                () -> new IllegalArgumentException("Platform namespace not found: " + namespaceId));
    String clusterId = connectionRegistry.resolveClusterId(entity.getId());
    if (isTemporalNamespaceRegistered(clusterId, entity.getId())) {
      return namespaceAlignment(false).items().stream()
          .filter(item -> item.id().equals(entity.getId()))
          .findFirst()
          .orElseThrow();
    }
    int retentionDays = properties.admin().defaultRetentionDays();
    try {
      connectionRegistry
          .clientsForCluster(clusterId)
          .serviceStubs()
          .blockingStub()
          .registerNamespace(
              RegisterNamespaceRequest.newBuilder()
                  .setNamespace(entity.getId())
                  .setWorkflowExecutionRetentionPeriod(
                      com.google.protobuf.Duration.newBuilder()
                          .setSeconds(Duration.ofDays(retentionDays).toSeconds())
                          .build())
                  .build());
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Failed to register Temporal namespace "
              + entity.getId()
              + " on cluster "
              + clusterId
              + ": "
              + e.getMessage(),
          e);
    }
    auditLogService.record(
        new AuditLogService.AuditLogEntry(
            Instant.now(),
            null,
            adminAccessService.actorApiKeyId(),
            AuditActions.TEMPORAL_NAMESPACE_REGISTERED,
            "temporal_namespace",
            entity.getId(),
            entity.getId(),
            null,
            null,
            null,
            "cluster=" + clusterId + ",retentionDays=" + retentionDays,
            null));
    taskQueueCache.clear();
    return namespaceAlignment(false).items().stream()
        .filter(item -> item.id().equals(entity.getId()))
        .findFirst()
        .orElseThrow();
  }

  @Transactional(readOnly = true)
  public TemporalWorkerPageDto workers(boolean refreshTaskQueues) {
    adminAccessService.requireAdmin();
    if (refreshTaskQueues) {
      taskQueueCache.clear();
    }
    Map<String, DeploymentContract> contracts = contractsByNamespace();
    List<TemporalWorkerRowDto> rows = new ArrayList<>();
    for (DeploymentContract contract : contracts.values()) {
      String clusterId = connectionRegistry.resolveClusterId(contract.namespace());
      TemporalClusterEntity cluster = requireCluster(clusterId);
      TaskQueueStats stats = taskQueueStats(clusterId, contract.namespace(), contract.taskQueue());
      TaskQueueStats platformStats =
          taskQueueStats(clusterId, contract.namespace(), PLATFORM_TASK_QUEUE);
      rows.add(
          new TemporalWorkerRowDto(
              contract.appId(),
              contract.namespace(),
              contract.taskQueue(),
              clusterId,
              cluster.getDisplayName(),
              "redis-contract",
              isContractAlive(contract.namespace()),
              null,
              stats.workflowPollers(),
              stats.activityPollers(),
              new TemporalPlatformQueueDto(PLATFORM_TASK_QUEUE, platformStats.workflowPollers())));
    }
    rows.sort(
        Comparator.comparing(TemporalWorkerRowDto::namespace)
            .thenComparing(TemporalWorkerRowDto::taskQueue));
    return new TemporalWorkerPageDto(rows);
  }

  @Transactional(readOnly = true)
  public TemporalSchedulePageDto schedules(String namespaceFilter) {
    adminAccessService.requireAdmin();
    List<TemporalScheduleDto> items = new ArrayList<>();
    List<PlatformNamespaceEntity> namespaces =
        namespaceRepository.findAll().stream()
            .sorted(Comparator.comparing(PlatformNamespaceEntity::getId))
            .filter(
                ns ->
                    namespaceFilter == null
                        || namespaceFilter.isBlank()
                        || ns.getId().equals(namespaceFilter.trim()))
            .toList();
    for (PlatformNamespaceEntity namespace : namespaces) {
      String clusterId = connectionRegistry.resolveClusterId(namespace.getId());
      if (!isTemporalNamespaceRegistered(clusterId, namespace.getId())) {
        continue;
      }
      try {
        ScheduleClient scheduleClient =
            connectionRegistry.clientsForPlatformNamespace(namespace.getId()).scheduleClient(
                namespace.getId());
        scheduleClient
            .listSchedules()
            .forEach(
                description -> {
                  String scheduleId = description.getScheduleId();
                  String workflowId =
                      scheduleId.startsWith("flowfoundry-timer-start-")
                          ? scheduleId.substring("flowfoundry-timer-start-".length())
                          : scheduleId;
                  var state = description.getSchedule().getState();
                  var nextTimes = description.getInfo().getNextActionTimes();
                  items.add(
                      new TemporalScheduleDto(
                          scheduleId,
                          namespace.getId(),
                          clusterId,
                          workflowId,
                          state != null && state.isPaused() ? "PAUSED" : "RUNNING",
                          state == null ? null : state.getNote(),
                          nextTimes == null || nextTimes.isEmpty() ? null : nextTimes.get(0)));
                });
      } catch (Exception e) {
        log.warn(
            "Failed to list schedules for namespace {}: {}", namespace.getId(), e.toString());
      }
    }
    return new TemporalSchedulePageDto(items);
  }

  @Transactional
  public void pauseSchedule(String namespaceId, String scheduleId) {
    adminAccessService.requireAdmin();
    mutateSchedule(namespaceId, scheduleId, true);
    auditSchedule(namespaceId, scheduleId, AuditActions.TEMPORAL_SCHEDULE_PAUSED);
  }

  @Transactional
  public void resumeSchedule(String namespaceId, String scheduleId) {
    adminAccessService.requireAdmin();
    mutateSchedule(namespaceId, scheduleId, false);
    auditSchedule(namespaceId, scheduleId, AuditActions.TEMPORAL_SCHEDULE_RESUMED);
  }

  public void registerTemporalNamespaceIfConfigured(String namespaceId) {
    if (!properties.admin().autoRegisterNamespace()) {
      return;
    }
    try {
      registerTemporalNamespace(namespaceId);
    } catch (Exception e) {
      log.warn(
          "Auto-register Temporal namespace failed for {}: {}", namespaceId, e.toString());
    }
  }

  public String runtimeStatusForNamespace(String namespaceId) {
    try {
      String clusterId = connectionRegistry.resolveClusterId(namespaceId);
      boolean temporalRegistered = isTemporalNamespaceRegistered(clusterId, namespaceId);
      DeploymentContract contract = contractsByNamespace().get(namespaceId);
      TemporalWorkerStatusDto worker = toWorkerStatus(contract, namespaceId);
      return resolveStatus(temporalRegistered, worker);
    } catch (Exception e) {
      return "UNKNOWN";
    }
  }

  private void mutateSchedule(String namespaceId, String scheduleId, boolean pause) {
    if (namespaceId == null || namespaceId.isBlank()) {
      throw new IllegalArgumentException("namespace is required");
    }
    if (scheduleId == null || scheduleId.isBlank()) {
      throw new IllegalArgumentException("scheduleId is required");
    }
    ScheduleClient scheduleClient =
        connectionRegistry.clientsForPlatformNamespace(namespaceId).scheduleClient(namespaceId);
    var handle = scheduleClient.getHandle(scheduleId);
    if (pause) {
      handle.pause("Paused from FlowFoundry Temporal admin");
    } else {
      handle.unpause();
    }
  }

  private void auditSchedule(String namespaceId, String scheduleId, String action) {
    auditLogService.record(
        new AuditLogService.AuditLogEntry(
            Instant.now(),
            null,
            adminAccessService.actorApiKeyId(),
            action,
            "temporal_schedule",
            scheduleId,
            namespaceId,
            null,
            null,
            null,
            "scheduleId=" + scheduleId,
            null));
  }

  private TemporalSummaryCountsDto summarize(List<TemporalNamespaceAlignmentDto> items) {
    int platformNamespaces = 0;
    int temporalNamespaces = 0;
    int ready = 0;
    int noTemporalNs = 0;
    int noWorker = 0;
    int staleWorkers = 0;
    int orphanTemporal = 0;
    for (TemporalNamespaceAlignmentDto item : items) {
      if (item.platformRegistered()) {
        platformNamespaces++;
      }
      if (item.temporalRegistered()) {
        temporalNamespaces++;
      }
      switch (item.status()) {
        case "READY" -> ready++;
        case "NO_TEMPORAL_NS" -> noTemporalNs++;
        case "NO_WORKER" -> noWorker++;
        case "STALE_WORKER" -> staleWorkers++;
        case "ORPHAN_TEMPORAL" -> orphanTemporal++;
        default -> {}
      }
    }
    return new TemporalSummaryCountsDto(
        platformNamespaces,
        temporalNamespaces,
        ready,
        noTemporalNs,
        noWorker,
        staleWorkers,
        orphanTemporal);
  }

  private Map<String, DeploymentContract> contractsByNamespace() {
    Map<String, DeploymentContract> contracts = new LinkedHashMap<>();
    for (DeploymentContract contract : contractRegistry.registeredContracts()) {
      contracts.put(contract.namespace(), contract);
    }
    try {
      DeploymentContract local = contractRegistry.localContract();
      contracts.putIfAbsent(local.namespace(), local);
    } catch (Exception ignored) {
      // No local contract in some test profiles.
    }
    return contracts;
  }

  private Map<String, Set<String>> loadTemporalNamespacesByCluster() {
    Map<String, Set<String>> result = new HashMap<>();
    for (TemporalClusterEntity cluster : clusterRepository.findAll()) {
      result.put(cluster.getId(), listTemporalNamespaces(cluster.getId()));
    }
    return result;
  }

  private Set<String> listTemporalNamespaces(String clusterId) {
    Set<String> namespaces = new HashSet<>();
    try {
      ListNamespacesResponse response =
          connectionRegistry
              .clientsForCluster(clusterId)
              .serviceStubs()
              .blockingStub()
              .listNamespaces(ListNamespacesRequest.newBuilder().setPageSize(200).build());
      response
          .getNamespacesList()
          .forEach(info -> namespaces.add(info.getNamespaceInfo().getName()));
    } catch (Exception e) {
      log.warn("Failed to list Temporal namespaces on cluster {}: {}", clusterId, e.toString());
    }
    return namespaces;
  }

  private boolean isTemporalNamespaceRegistered(String clusterId, String namespaceId) {
    return listTemporalNamespaces(clusterId).contains(namespaceId);
  }

  private boolean isContractAlive(String namespaceId) {
    try {
      String value = redis.opsForValue().get(CONTRACT_PREFIX + namespaceId);
      return value != null && !value.isBlank();
    } catch (Exception e) {
      return false;
    }
  }

  private TemporalWorkerStatusDto toWorkerStatus(DeploymentContract contract, String namespaceId) {
    if (contract == null) {
      return null;
    }
    boolean alive = isContractAlive(namespaceId);
    return new TemporalWorkerStatusDto(
        contract.appId(), contract.namespace(), contract.taskQueue(), alive, null);
  }

  private String resolveStatus(boolean temporalRegistered, TemporalWorkerStatusDto worker) {
    if (!temporalRegistered) {
      return "NO_TEMPORAL_NS";
    }
    if (worker == null) {
      return "NO_WORKER";
    }
    if (!worker.alive()) {
      return "STALE_WORKER";
    }
    return "READY";
  }

  private int pollCount(String clusterId, String namespaceId, DeploymentContract contract) {
    if (contract == null) {
      return 0;
    }
    TaskQueueStats stats = taskQueueStats(clusterId, namespaceId, contract.taskQueue());
    return stats.workflowPollers() + stats.activityPollers();
  }

  private TaskQueueStats taskQueueStats(String clusterId, String namespaceId, String taskQueue) {
    String cacheKey = clusterId + "|" + namespaceId + "|" + taskQueue;
    CachedTaskQueueStats cached = taskQueueCache.get(cacheKey);
    Instant now = Instant.now();
    if (cached != null && cached.expiresAt().isAfter(now)) {
      return cached.stats();
    }
    TaskQueueStats stats = describeTaskQueue(clusterId, namespaceId, taskQueue);
    taskQueueCache.put(
        cacheKey, new CachedTaskQueueStats(stats, now.plusSeconds(30)));
    return stats;
  }

  private TaskQueueStats describeTaskQueue(String clusterId, String namespaceId, String taskQueue) {
    if (taskQueue == null || taskQueue.isBlank()) {
      return new TaskQueueStats(0, 0);
    }
    try {
      DescribeTaskQueueResponse workflowResponse =
          describe(clusterId, namespaceId, taskQueue, TaskQueueType.TASK_QUEUE_TYPE_WORKFLOW);
      DescribeTaskQueueResponse activityResponse =
          describe(clusterId, namespaceId, taskQueue, TaskQueueType.TASK_QUEUE_TYPE_ACTIVITY);
      return new TaskQueueStats(
          workflowResponse.getPollersCount(), activityResponse.getPollersCount());
    } catch (Exception e) {
      log.debug(
          "DescribeTaskQueue failed cluster={} namespace={} queue={}: {}",
          clusterId,
          namespaceId,
          taskQueue,
          e.toString());
      return new TaskQueueStats(0, 0);
    }
  }

  private DescribeTaskQueueResponse describe(
      String clusterId, String namespaceId, String taskQueue, TaskQueueType type) {
    return connectionRegistry
        .clientsForCluster(clusterId)
        .serviceStubs()
        .blockingStub()
        .describeTaskQueue(
            DescribeTaskQueueRequest.newBuilder()
                .setNamespace(namespaceId)
                .setTaskQueue(TaskQueue.newBuilder().setName(taskQueue).build())
                .setTaskQueueType(type)
                .build());
  }

  private TemporalClusterEntity requireDefaultCluster() {
    return clusterRepository
        .findByDefaultClusterTrue()
        .orElseGet(
            () ->
                clusterRepository
                    .findById(TemporalClusterBootstrapRunner.DEFAULT_CLUSTER_ID)
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "Default Temporal cluster is not configured")));
  }

  private TemporalClusterEntity requireCluster(String clusterId) {
    return clusterRepository
        .findById(clusterId)
        .orElseThrow(() -> new IllegalArgumentException("Temporal cluster not found: " + clusterId));
  }

  private static String buildNamespaceUiUrl(TemporalClusterEntity cluster, String namespaceId) {
    return TemporalClusterAdminService.resolvedUiBaseUrl(cluster)
        + "/namespaces/"
        + namespaceId;
  }

  private record TaskQueueStats(int workflowPollers, int activityPollers) {}

  private record CachedTaskQueueStats(TaskQueueStats stats, Instant expiresAt) {}
}
