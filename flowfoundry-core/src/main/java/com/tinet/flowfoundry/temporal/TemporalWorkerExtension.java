package com.tinet.flowfoundry.temporal;

import io.temporal.worker.Worker;

/** 业务 Demo 向平台 Temporal Worker 注册 Workflow / Activity 实现。 */
public interface TemporalWorkerExtension {

  void register(Worker worker);
}
