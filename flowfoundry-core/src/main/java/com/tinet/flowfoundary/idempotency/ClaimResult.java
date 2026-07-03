package com.example.platform.idempotency;

public enum ClaimResult {
  /** 首次占位成功，可以执行业务 */
  CLAIMED,
  /** 已完成，直接返回 */
  ALREADY_DONE,
  /** 其他执行正在进行 */
  IN_PROGRESS
}
