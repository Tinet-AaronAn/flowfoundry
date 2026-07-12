package com.tinet.flowfoundry.temporal;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import io.temporal.api.workflowservice.v1.GetSystemInfoRequest;
import io.temporal.api.workflowservice.v1.GetSystemInfoResponse;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnFlowFoundryPlatform
public class TemporalClusterProbe {

  private final TemporalConnectionRegistry connectionRegistry;

  public TemporalClusterProbe(TemporalConnectionRegistry connectionRegistry) {
    this.connectionRegistry = connectionRegistry;
  }

  public ProbeResult probe(String clusterId) {
    try {
      GetSystemInfoResponse response =
          connectionRegistry
              .clientsForCluster(clusterId)
              .serviceStubs()
              .blockingStub()
              .getSystemInfo(GetSystemInfoRequest.newBuilder().build());
      String version = response.getServerVersion();
      if (version == null || version.isBlank()) {
        version = null;
      }
      return new ProbeResult(true, version);
    } catch (Exception ignored) {
      return new ProbeResult(false, null);
    }
  }

  public record ProbeResult(boolean reachable, String serverVersion) {}
}
