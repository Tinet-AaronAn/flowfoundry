package com.tinet.flowfoundry.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * FlowFoundry 平台 SDK 统一配置项。业务应用通过 {@code flowfoundry.*} 前缀覆盖默认值。
 */
@ConfigurationProperties(prefix = "flowfoundry")
public class FlowFoundryProperties {

  @NestedConfigurationProperty
  private ActivityRegistry activityRegistry = new ActivityRegistry();

  @NestedConfigurationProperty private Modeler modeler = new Modeler();

  public ActivityRegistry getActivityRegistry() {
    return activityRegistry;
  }

  public void setActivityRegistry(ActivityRegistry activityRegistry) {
    this.activityRegistry = activityRegistry;
  }

  public Modeler getModeler() {
    return modeler;
  }

  public void setModeler(Modeler modeler) {
    this.modeler = modeler;
  }

  /** Activity 注册表路径（优先于 legacy {@code platform.activity-registry.path}）。 */
  public static class ActivityRegistry {
    private String path = "classpath:activities-registry.yaml";

    public String getPath() {
      return path;
    }

    public void setPath(String path) {
      this.path = path;
    }
  }

  /** 建模器与 iframe 嵌入相关配置。 */
  public static class Modeler {
    private boolean embedEnabled = true;
    private String apiBase = "/api";
    private String embedPath = "/modeler/embed.html";
    private String sdkScriptPath = "/assets/js/flowfoundry-modeler-sdk.js";
    private boolean allowFrameEmbedding = true;
    private List<String> allowedEmbedOrigins = new ArrayList<>();

    public boolean isEmbedEnabled() {
      return embedEnabled;
    }

    public void setEmbedEnabled(boolean embedEnabled) {
      this.embedEnabled = embedEnabled;
    }

    public String getApiBase() {
      return apiBase;
    }

    public void setApiBase(String apiBase) {
      this.apiBase = apiBase;
    }

    public String getEmbedPath() {
      return embedPath;
    }

    public void setEmbedPath(String embedPath) {
      this.embedPath = embedPath;
    }

    public String getSdkScriptPath() {
      return sdkScriptPath;
    }

    public void setSdkScriptPath(String sdkScriptPath) {
      this.sdkScriptPath = sdkScriptPath;
    }

    public boolean isAllowFrameEmbedding() {
      return allowFrameEmbedding;
    }

    public void setAllowFrameEmbedding(boolean allowFrameEmbedding) {
      this.allowFrameEmbedding = allowFrameEmbedding;
    }

    public List<String> getAllowedEmbedOrigins() {
      return allowedEmbedOrigins;
    }

    public void setAllowedEmbedOrigins(List<String> allowedEmbedOrigins) {
      this.allowedEmbedOrigins = allowedEmbedOrigins == null ? new ArrayList<>() : allowedEmbedOrigins;
    }
  }
}
