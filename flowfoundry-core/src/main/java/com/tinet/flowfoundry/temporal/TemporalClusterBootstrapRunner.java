package com.tinet.flowfoundry.temporal;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import com.tinet.flowfoundry.config.TemporalProperties;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnFlowFoundryPlatform
public class TemporalClusterBootstrapRunner {

  private static final Logger log = LoggerFactory.getLogger(TemporalClusterBootstrapRunner.class);
  public static final String DEFAULT_CLUSTER_ID = "default";

  private final TemporalClusterRepository clusterRepository;
  private final TemporalProperties properties;

  public TemporalClusterBootstrapRunner(
      TemporalClusterRepository clusterRepository, TemporalProperties properties) {
    this.clusterRepository = clusterRepository;
    this.properties = properties;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void ensureDefaultCluster() {
    Instant now = Instant.now();
    TemporalClusterEntity cluster =
        clusterRepository.findById(DEFAULT_CLUSTER_ID).orElseGet(TemporalClusterEntity::new);
    boolean created = cluster.getId() == null;
    cluster.setId(DEFAULT_CLUSTER_ID);
    cluster.setDisplayName(
        created ? "Default Temporal Cluster" : cluster.getDisplayName());
    cluster.setHost(properties.host());
    if (cluster.getUiBaseUrl() == null || cluster.getUiBaseUrl().isBlank()) {
      cluster.setUiBaseUrl(properties.resolvedUiBaseUrl());
    }
    cluster.setDefaultCluster(true);
    if (created) {
      cluster.setCreatedAt(now);
    }
    cluster.setUpdatedAt(now);
    clusterRepository.save(cluster);
    if (created) {
      log.info(
          "Seeded default Temporal cluster id={} host={} ui={}",
          cluster.getId(),
          cluster.getHost(),
          cluster.getUiBaseUrl());
    }
  }
}
