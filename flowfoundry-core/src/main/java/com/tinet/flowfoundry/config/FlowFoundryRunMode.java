package com.tinet.flowfoundry.config;

/** FlowFoundry 进程角色：平台 HTTP 服务或业务 Temporal Worker。 */
public final class FlowFoundryRunMode {

  public static final String PLATFORM = "platform";
  public static final String WORKER = "worker";

  private FlowFoundryRunMode() {}
}
