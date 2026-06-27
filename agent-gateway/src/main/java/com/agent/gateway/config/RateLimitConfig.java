package com.agent.gateway.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 限流 Bucket 注册表：
 *  - 每个 key（tenantId 或 IP）对应一个独立 Bucket
 *  - Bucket4j 8.10.1 API：Bandwidth.builder().capacity(...).refillIntervally(...)
 */
@Component
public class RateLimitConfig {

    private final RateLimitProperties properties;
    private final ConcurrentMap<String, Bucket> bucketRegistry = new ConcurrentHashMap<>();

    public RateLimitConfig(RateLimitProperties properties) {
        this.properties = properties;
    }

    /**
     * 获取或创建指定 key 的 Bucket。
     */
    public Bucket getBucket(String key) {
        return bucketRegistry.computeIfAbsent(key, k -> {
            Bandwidth limit = Bandwidth.builder()
                    .capacity(properties.getCapacity())
                    .refillIntervally(properties.getRefillTokens(), Duration.ofSeconds(properties.getRefillSeconds()))
                    .build();
            return Bucket.builder().addLimit(limit).build();
        });
    }
}
