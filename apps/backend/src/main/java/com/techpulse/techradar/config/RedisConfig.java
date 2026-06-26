package com.techpulse.techradar.config;

import org.springframework.context.annotation.Configuration;

/**
 * Redis is configured via spring.redis.* in application.yml.
 * Spring Boot auto-configures ReactiveStringRedisTemplate (String key+value)
 * which is used by TokenBlacklistService, ReactiveRedisCache, and ChatRateLimiterService.
 */
@Configuration
public class RedisConfig {
}