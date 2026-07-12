package com.tinet.flowfoundry.plugin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Parsed {@code META-INF/flowfoundry-plugin.yaml} (content under the {@code plugin:} root key).
 * See docs/plugin-runtime-design.md §3.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PluginDescriptor(
    String id,
    String version,
    String name,
    String description,
    List<String> basePackages,
    Requires requires,
    Temporal temporal,
    Capabilities capabilities,
    Runtime runtime) {

  public PluginDescriptor {
    basePackages = basePackages == null ? List.of() : List.copyOf(basePackages);
  }

  public boolean typedWorkflows() {
    return capabilities != null && Boolean.TRUE.equals(capabilities.typedWorkflows());
  }

  public int defaultReplicas() {
    if (runtime == null || runtime.replicas() == null) {
      return 1;
    }
    return runtime.replicas();
  }

  public String declaredNamespace() {
    return temporal == null ? null : temporal.namespace();
  }

  public String requiredSdkVersion() {
    return requires == null ? null : requires.sdkVersion();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Requires(String sdkVersion) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Temporal(String namespace) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Capabilities(Boolean typedWorkflows) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Runtime(Integer replicas, Resources resources) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Resources(String memory, String cpu) {}

  /** Root wrapper matching the {@code plugin:} key in the descriptor yaml. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Manifest(PluginDescriptor plugin) {}
}
