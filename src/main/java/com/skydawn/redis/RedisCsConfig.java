package com.skydawn.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisCsConfig {

    @Bean
    public RedisFinder redisFinder(StringRedisTemplate stringRedisTemplate) {
        return new RedisFinder(stringRedisTemplate);
    }

    @Bean
    public RedisOperation redisOperation(StringRedisTemplate stringRedisTemplate) {
        return new RedisOperation(stringRedisTemplate);
    }
}
