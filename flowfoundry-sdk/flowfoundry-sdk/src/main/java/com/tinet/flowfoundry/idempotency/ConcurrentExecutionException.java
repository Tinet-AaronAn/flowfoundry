package com.tinet.flowfoundry.idempotency;

/** 并发执行 detected，Temporal 会退避重试 */
public class ConcurrentExecutionException extends RuntimeException {

  public ConcurrentExecutionException(String message) {
    super(message);
  }
}
