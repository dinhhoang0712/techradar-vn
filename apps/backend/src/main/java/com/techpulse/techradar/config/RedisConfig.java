package com.techpulse.techradar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;

/**
 * Redis is configured via spring.redis.* in application.yml.
 * Spring Boot auto-configures ReactiveStringRedisTemplate (String key+value)
 * which is used by TokenBlacklistService, ReactiveRedisCache, and ChatRateLimiterService.
 */
@Configuration
public class RedisConfig {

    /**
     * Backs cross-instance SSE fan-out (MessageBroadcaster, NotificationService): a publish() on
     * any backend instance broadcasts over Redis Pub/Sub, and every instance's listener delivers
     * to whichever local SSE subscribers it happens to be holding.
     */
    @Bean
    public ReactiveRedisMessageListenerContainer reactiveRedisMessageListenerContainer(
            ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveRedisMessageListenerContainer(connectionFactory);
    }
}