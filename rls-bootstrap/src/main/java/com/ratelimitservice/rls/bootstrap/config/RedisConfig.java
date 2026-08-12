package com.ratelimitservice.rls.bootstrap.config;

import com.ratelimitservice.rls.adapter.redis.RedisRateLimitEvaluationAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Wires the spec 2 Redis adapter by hand (design decision 5: no autoconfiguration starters for
 * infrastructure this project treats as an explicit hexagonal port).
 */
@Configuration
public class RedisConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(@Value("${rls.redis.host}") String host,
                                                             @Value("${rls.redis.port}") int port) {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
    }

    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(LettuceConnectionFactory connectionFactory) {
        return new ReactiveRedisTemplate<>(connectionFactory, RedisSerializationContext.string());
    }

    @Bean
    public RedisRateLimitEvaluationAdapter redisRateLimitEvaluationAdapter(
            ReactiveRedisTemplate<String, String> reactiveRedisTemplate) {
        return new RedisRateLimitEvaluationAdapter(reactiveRedisTemplate);
    }
}
