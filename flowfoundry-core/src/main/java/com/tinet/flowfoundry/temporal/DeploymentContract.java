package com.tinet.flowfoundry.temporal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 使用方（flowfoundry-app 模块）的部署契约。
 *
 * <p>Worker 启动时上报，平台据此把该使用方的 workflow 执行 / run logs 路由到其独立的 Temporal namespace。
 *
 * @param appId 使用方应用标识（一般为 Spring application name）
 * @param registryNamespace Activity 注册表 namespace（业务域标识，平台路由 key）
 * @param temporalNamespace 该使用方的 Temporal 物理 namespace（强隔离边界）
 * @param taskQueue 该使用方 Worker 轮询的 Task Queue（解释器 + Activity 均在此执行）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeploymentContract(
    String appId, String registryNamespace, String temporalNamespace, String taskQueue) {}
