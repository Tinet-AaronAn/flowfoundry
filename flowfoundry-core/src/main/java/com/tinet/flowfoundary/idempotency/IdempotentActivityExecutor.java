package com.tinet.flowfoundary.idempotency;

import com.tinet.flowfoundary.registry.ActivityRegistry;
import com.tinet.flowfoundary.registry.ActivityRegistry.ActivityDefinition;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于注册表 idempotency 配置，为 Activity 提供统一幂等执行模板。
 */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class IdempotentActivityExecutor {

  private static final Logger log = LoggerFactory.getLogger(IdempotentActivityExecutor.class);

  private final ActivityRegistry registry;
  private final RedisIdempotencyService idempotency;

  public IdempotentActivityExecutor(
      ActivityRegistry registry, RedisIdempotencyService idempotency) {
    this.registry = registry;
    this.idempotency = idempotency;
  }

  public <T> T execute(
      String activityId, Map<String, Object> keyVariables, Supplier<T> businessLogic) {
    ActivityDefinition def = registry.require(activityId);
    if (def.idempotency() == null || def.idempotency().keyPattern() == null) {
      return businessLogic.get();
    }

    String key = idempotency.buildKey(def.idempotency().keyPattern(), keyVariables);
    Duration ttl = def.idempotencyTtl();

    ClaimResult claim = idempotency.tryClaim(key, ttl);
    switch (claim) {
      case ALREADY_DONE -> {
        log.info("Skip activity {} — already completed key={}", activityId, key);
        return null;
      }
      case IN_PROGRESS -> throw new ConcurrentExecutionException(
          "Activity " + activityId + " in progress for key=" + key);
      case CLAIMED -> {
        try {
          T result = businessLogic.get();
          idempotency.markCompleted(key, ttl);
          return result;
        } catch (RuntimeException e) {
          idempotency.release(key);
          throw e;
        }
      }
      default -> throw new IllegalStateException("Unexpected claim result: " + claim);
    }
  }

  public void executeVoid(
      String activityId, Map<String, Object> keyVariables, Runnable businessLogic) {
    execute(
        activityId,
        keyVariables,
        () -> {
          businessLogic.run();
          return null;
        });
  }
}
