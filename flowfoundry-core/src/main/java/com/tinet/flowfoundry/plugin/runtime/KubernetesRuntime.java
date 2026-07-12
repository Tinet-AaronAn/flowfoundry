package com.tinet.flowfoundry.plugin.runtime;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryPlatform;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.HTTPGetActionBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Applies plugin runner Deployments to the configured Kubernetes cluster. */
@Service
@ConditionalOnFlowFoundryPlatform
@ConditionalOnProperty(prefix = "flowfoundry.plugins.runtime", name = "enabled", havingValue = "true")
public class KubernetesRuntime implements PluginRuntimeManager {

  private static final Logger log = LoggerFactory.getLogger(KubernetesRuntime.class);
  private static final String LABEL_APP = "app";
  private static final String LABEL_PLUGIN_ID = "flowfoundry.io/plugin-id";
  private static final String APP_VALUE = "flowfoundry-plugin";

  private final PluginRuntimeProperties properties;
  private final PluginDownloadTokenService downloadTokenService;
  private volatile KubernetesClient client;

  public KubernetesRuntime(
      PluginRuntimeProperties properties, PluginDownloadTokenService downloadTokenService) {
    this.properties = properties;
    this.downloadTokenService = downloadTokenService;
  }

  private KubernetesClient kubernetesClient() {
    KubernetesClient existing = client;
    if (existing != null) {
      return existing;
    }
    synchronized (this) {
      if (client == null) {
        client = new KubernetesClientBuilder().build();
      }
      return client;
    }
  }

  @Override
  public void apply(PluginDeployment deployment) {
    ensureNamespace();
    String namespace = properties.resolvedKubernetesNamespace();
    String name = deployment.deploymentName();
    Deployment desired = buildDeployment(deployment);
    Deployment existing =
        kubernetesClient().apps().deployments().inNamespace(namespace).withName(name).get();
    if (existing == null) {
      kubernetesClient().resource(desired).inNamespace(namespace).create();
      log.info(
          "Created plugin deployment name={} plugin={} version={} replicas={}",
          name,
          deployment.pluginId(),
          deployment.version(),
          deployment.effectiveReplicas());
      return;
    }
    kubernetesClient().resource(desired).inNamespace(namespace).update();
    log.info(
        "Updated plugin deployment name={} plugin={} version={} replicas={}",
        name,
        deployment.pluginId(),
        deployment.version(),
        deployment.effectiveReplicas());
  }

  @Override
  public void delete(PluginDeployment deployment) {
    String namespace = properties.resolvedKubernetesNamespace();
    kubernetesClient()
        .apps()
        .deployments()
        .inNamespace(namespace)
        .withName(deployment.deploymentName())
        .delete();
    log.info("Deleted plugin deployment name={}", deployment.deploymentName());
  }

  @Override
  public RuntimeStatus probe(PluginDeployment deployment) {
    Deployment current =
        kubernetesClient()
            .apps()
            .deployments()
            .inNamespace(properties.resolvedKubernetesNamespace())
            .withName(deployment.deploymentName())
            .get();
    if (current == null) {
      return RuntimeStatus.absent();
    }
    DeploymentStatus status = current.getStatus();
    int ready = status == null || status.getReadyReplicas() == null ? 0 : status.getReadyReplicas();
    int desired =
        current.getSpec() == null || current.getSpec().getReplicas() == null
            ? 0
            : current.getSpec().getReplicas();
    boolean healthy = deployment.effectiveReplicas() == 0 || ready >= 1;
    String summary =
        "readyReplicas="
            + ready
            + "/"
            + desired
            + (status != null && status.getUnavailableReplicas() != null
                ? " unavailable=" + status.getUnavailableReplicas()
                : "");
    return new RuntimeStatus(true, ready, desired, healthy, summary);
  }

  @Override
  public String fetchLogs(PluginDeployment deployment, int tailLines) {
    String namespace = properties.resolvedKubernetesNamespace();
    var pods =
        kubernetesClient()
            .pods()
            .inNamespace(namespace)
            .withLabel(LABEL_PLUGIN_ID, deployment.pluginId())
            .list()
            .getItems();
    if (pods.isEmpty()) {
      return "No pods found for plugin " + deployment.pluginId();
    }
    StringBuilder builder = new StringBuilder();
    int limit = Math.max(tailLines, 1);
    for (var pod : pods) {
      String podName = pod.getMetadata().getName();
      String logs =
          kubernetesClient()
              .pods()
              .inNamespace(namespace)
              .withName(podName)
              .inContainer("runner")
              .tailingLines(limit)
              .getLog();
      builder.append("=== ").append(podName).append(" ===\n");
      builder.append(logs == null ? "" : logs).append('\n');
    }
    return builder.toString();
  }

  private Deployment buildDeployment(PluginDeployment deployment) {
    String token = downloadTokenService.token(deployment.pluginId(), deployment.version(), deployment.jarSha256());
    String downloadUrl =
        properties.resolvedPlatformUrl()
            + "/api/internal/plugins/"
            + deployment.pluginId()
            + "/"
            + deployment.version()
            + "/package";
    Map<String, String> labels = deploymentLabels(deployment.pluginId());
    String initScript =
        "set -euo pipefail; "
            + "curl -fsSL -H \"X-Plugin-Download-Token: "
            + token
            + "\" \""
            + downloadUrl
            + "\" -o /plugin/plugin.jar; "
            + "test -s /plugin/plugin.jar";

    return new DeploymentBuilder()
        .withNewMetadata()
        .withName(deployment.deploymentName())
        .withLabels(labels)
        .endMetadata()
        .withNewSpec()
        .withReplicas(deployment.effectiveReplicas())
        .withNewSelector()
        .addToMatchLabels(labels)
        .endSelector()
        .withNewTemplate()
        .withNewMetadata()
        .withLabels(labels)
        .addToLabels("flowfoundry.io/plugin-version", deployment.version())
        .endMetadata()
        .withNewSpec()
        .withTerminationGracePeriodSeconds((long) properties.resolvedTerminationGraceSeconds())
        .addNewInitContainer()
        .withName("fetch-plugin")
        .withImage("curlimages/curl:8.5.0")
        .withCommand("sh", "-c")
        .withArgs(initScript)
        .withVolumeMounts(new VolumeMountBuilder().withName("plugin-jar").withMountPath("/plugin").build())
        .endInitContainer()
        .addToContainers(
            new ContainerBuilder()
                .withName("runner")
                .withImage(properties.resolvedRunnerImage())
                .withImagePullPolicy("IfNotPresent")
                .withCommand("sh", "-c")
                .withArgs(
                    "exec java -Dloader.path=/plugin/plugin.jar -jar /app/flowfoundry-plugin-runner.jar")
                .withPorts(new ContainerPortBuilder().withContainerPort(8080).withName("http").build())
                .withEnv(
                    env("TEMPORAL_HOST", deployment.temporalHost()).build(),
                    env("FLOWFOUNDRY_PLUGIN_SCAN_PACKAGES", deployment.scanPackagesEnv()).build(),
                    env("SPRING_DATA_REDIS_HOST", properties.resolvedRedisHost()).build(),
                    env("PLUGIN_RUNNER_PORT", "8080").build())
                .withVolumeMounts(
                    new VolumeMountBuilder().withName("plugin-jar").withMountPath("/plugin").build())
                .withReadinessProbe(
                    new ProbeBuilder()
                        .withHttpGet(
                            new HTTPGetActionBuilder()
                                .withPath("/actuator/health/readiness")
                                .withPort(new IntOrString(8080))
                                .build())
                        .withInitialDelaySeconds(20)
                        .withPeriodSeconds(10)
                        .build())
                .withLivenessProbe(
                    new ProbeBuilder()
                        .withHttpGet(
                            new HTTPGetActionBuilder()
                                .withPath("/actuator/health/liveness")
                                .withPort(new IntOrString(8080))
                                .build())
                        .withInitialDelaySeconds(60)
                        .withPeriodSeconds(20)
                        .build())
                .withResources(
                    new ResourceRequirementsBuilder()
                        .addToRequests("memory", new Quantity(deployment.memoryRequest()))
                        .addToRequests("cpu", new Quantity(deployment.cpuRequest()))
                        .build())
                .build())
        .withVolumes(new VolumeBuilder().withName("plugin-jar").withNewEmptyDir().endEmptyDir().build())
        .endSpec()
        .endTemplate()
        .endSpec()
        .build();
  }

  private static EnvVarBuilder env(String name, String value) {
    return new EnvVarBuilder().withName(name).withValue(value);
  }

  private static Map<String, String> deploymentLabels(String pluginId) {
    Map<String, String> labels = new HashMap<>();
    labels.put(LABEL_APP, APP_VALUE);
    labels.put(LABEL_PLUGIN_ID, pluginId);
    return labels;
  }

  private void ensureNamespace() {
    String namespace = properties.resolvedKubernetesNamespace();
    if (kubernetesClient().namespaces().withName(namespace).get() != null) {
      return;
    }
    kubernetesClient()
        .namespaces()
        .resource(
            new io.fabric8.kubernetes.api.model.NamespaceBuilder()
                .withNewMetadata()
                .withName(namespace)
                .endMetadata()
                .build())
        .create();
    log.info("Created Kubernetes namespace {}", namespace);
  }
}
