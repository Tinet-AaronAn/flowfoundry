package com.example.platform.config;

import com.example.platform.registry.ActivityRegistry;
import com.example.platform.registry.ActivityRegistryLoader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties({TemporalProperties.class, ActivityRegistryProperties.class})
public class PlatformConfiguration {

  @Bean
  ActivityRegistry activityRegistry(ActivityRegistryLoader loader) {
    return loader.load();
  }

  @Bean
  StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
    return new StringRedisTemplate(factory);
  }
}
