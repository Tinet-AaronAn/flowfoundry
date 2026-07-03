package com.tinet.flowfoundary.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis SET NX 原子占位，解决「先查后写」竞态。
 *
 * <p>状态：PROCESSING -> COMPLETED；失败时 delete 释放。
 */
@Service
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisIdempotencyService {

  private static final String PREFIX = "idempotency:";
  private static final String STATUS_PROCESSING = "PROCESSING";
  private static final String STATUS_COMPLETED = "COMPLETED";

  private final StringRedisTemplate redis;

  public RedisIdempotencyService(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public ClaimResult tryClaim(String key, Duration ttl) {
    String redisKey = PREFIX + key;
    Boolean claimed =
        redis.opsForValue().setIfAbsent(redisKey, STATUS_PROCESSING, ttl);

    if (Boolean.TRUE.equals(claimed)) {
      return ClaimResult.CLAIMED;
    }

    String value = redis.opsForValue().get(redisKey);
    if (value == null) {
      // key 刚好过期，重试占位
      return tryClaim(key, ttl);
    }
    if (STATUS_COMPLETED.equals(value)) {
      return ClaimResult.ALREADY_DONE;
    }
    if (isStaleProcessing(value, ttl)) {
      redis.delete(redisKey);
      return tryClaim(key, ttl);
    }
    return ClaimResult.IN_PROGRESS;
  }

  public void markCompleted(String key, Duration ttl) {
    redis.opsForValue().set(PREFIX + key, STATUS_COMPLETED, ttl);
  }

  public void release(String key) {
    redis.delete(PREFIX + key);
  }

  public String buildKey(String pattern, Map<String, Object> variables) {
    String result = pattern;
    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
    }
    return result;
  }

  private boolean isStaleProcessing(String value, Duration ttl) {
    if (!value.startsWith(STATUS_PROCESSING + ":")) {
      return false;
    }
    try {
      long epoch = Long.parseLong(value.substring(STATUS_PROCESSING.length() + 1));
      return Instant.ofEpochMilli(epoch).isBefore(Instant.now().minus(ttl));
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
