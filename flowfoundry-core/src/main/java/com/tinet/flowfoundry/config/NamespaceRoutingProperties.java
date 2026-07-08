package com.tinet.flowfoundry.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Namespace 路由配置：区分「系统 namespace」（flowfoundry-core 平台管理专用）与业务 namespace（各
 * flowfoundry-app 使用方独立隔离）。详见 docs/detailed-design.md §11。
 *
 * <p>业务 namespace 的物理值由各使用方部署契约声明（{@code temporal.namespace}），平台仅作为不透明路由
 * key，不假设其语义（app 侧如何切分隔离单位由 app 自行决定）。
 */
@ConfigurationProperties(prefix = "flowfoundry.namespace")
public class NamespaceRoutingProperties {

  /** 系统 namespace：flowfoundry-core 平台管理相关 Temporal 资源使用（当前预留）。 */
  private String system = "flowfoundry-system";

  public String system() {
    return system == null || system.isBlank() ? "flowfoundry-system" : system.trim();
  }

  public void setSystem(String system) {
    this.system = system;
  }
}
