package com.insurance.platform.config;

import com.insurance.platform.redis.DefaultRedisKeyFactory;
import com.insurance.platform.redis.RedisKeyFactory;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RedisProperties.class)
public class RedisConfig {

    @Bean
    public RedisKeyFactory redisKeyFactory(RedisProperties properties) {
        return new DefaultRedisKeyFactory(properties.getEnvironment());
    }

    @Bean
    public Clock redisClock() {
        return Clock.systemUTC();
    }
}
