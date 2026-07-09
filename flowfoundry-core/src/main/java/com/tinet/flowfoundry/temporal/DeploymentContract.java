package com.tinet.flowfoundry.temporal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * App deployment contract published by a business Worker on startup.
 *
 * <p>{@code namespace} is the single isolation unit shared by FlowFoundry (workflows / RBAC),
 * Temporal (executions), and Activity Registry. {@code taskQueue} is derived from the registry
 * {@code defaultTaskQueue} — not configured separately on the Worker.
 *
 * @param appId Spring application name of the Worker module
 * @param namespace unified FlowFoundry + Temporal + registry namespace
 * @param taskQueue Worker task queue from Activity Registry {@code defaultTaskQueue}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeploymentContract(String appId, String namespace, String taskQueue) {}
