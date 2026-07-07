package com.agent.tool.engine.gateway;

import com.agent.tool.engine.config.ToolEngineProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis token-bucket rate limiter (doc 05-tool-engine §6.5).
 *
 * <p>Per-tenant + per-tool QPS limiting. Uses an atomic Lua script for
 * Redis-backed token acquisition (no race condition across instances).
 * When Redis is unavailable ({@code tool.cache.enabled=false} or Redis
 * throws), degrades to an in-memory sliding-window counter (per-instance
 * only, not distributed).</p>
 *
 * <p>Default QPS = {@code tool.ratelimit.defaultQps} (10). Each tool may
 * override via {@code ToolMeta.rateLimitQps} (T8 reserved; not yet wired).</p>
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    /**
     * Lua script: atomically decrement token bucket.
     *
     * <p>Args: [rate (QPS), capacity (= rate), now-seconds].
     * Returns 1 if token acquired, 0 if rejected.</p>
     */
    private static final String LUA_TOKEN_BUCKET = """
            local key = KEYS[1]
            local rate = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local data = redis.call('HMGET', key, 'tokens', 'last_refill')
            local tokens = tonumber(data[1])
            local last_refill = tonumber(data[2])
            if tokens == nil then
              tokens = capacity
              last_refill = now
            end
            local elapsed = math.max(0, now - last_refill)
            tokens = math.min(capacity, tokens + elapsed * rate)
            if tokens < 1 then
              redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
              redis.call('EXPIRE', key, 60)
              return 0
            end
            tokens = tokens - 1
            redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
            redis.call('EXPIRE', key, 60)
            return 1
            """;

    private final DefaultRedisScript<Long> tokenBucketScript;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ToolEngineProperties.RateLimit rateLimitProps;

    /** In-memory fallback: key -> {windowStart, count}. */
    private final Map<String, long[]> inMemoryWindows = new ConcurrentHashMap<>();

    public RateLimiter(@Autowired(required = false) RedisTemplate<String, Object> redisTemplate,
                       ToolEngineProperties properties) {
        this.redisTemplate = redisTemplate;
        this.rateLimitProps = properties.getRateLimit();
        this.tokenBucketScript = new DefaultRedisScript<>();
        this.tokenBucketScript.setScriptText(LUA_TOKEN_BUCKET);
        this.tokenBucketScript.setResultType(Long.class);
    }

    /** No-arg constructor for tests / when Redis is disabled. */
    public RateLimiter() {
        this(null, new ToolEngineProperties());
    }

    /**
     * Try to acquire one token for {@code tenantId + toolId}.
     *
     * @param tenantId tenant (null → "default")
     * @param toolId   tool id
     * @param qps      QPS limit (0 or negative → use defaultQps)
     * @return true if acquired (proceed), false if rate-limited (reject)
     */
    public boolean tryAcquire(String tenantId, String toolId, int qps) {
        String tenant = tenantId == null ? "default" : tenantId;
        int rate = qps > 0 ? qps : rateLimitProps.getDefaultQps();

        if (redisTemplate == null) {
            return tryAcquireInMemory(tenant, toolId, rate);
        }

        String key = "tool:ratelimit:" + tenant + ":" + toolId;
        try {
            Long result = redisTemplate.execute(
                    tokenBucketScript,
                    Collections.singletonList(key),
                    String.valueOf(rate),
                    String.valueOf(rate),
                    String.valueOf(System.currentTimeMillis() / 1000));
            boolean acquired = result != null && result == 1L;
            if (!acquired) {
                log.debug("rate limited (redis): tenant={}, tool={}, qps={}",
                        tenant, toolId, rate);
            }
            return acquired;
        } catch (Exception e) {
            // Intentional degradation: Redis failure falls back to in-memory rate-limiting.
            // ADR-006 compliant: catch + fallback is explicit degradation strategy, not a swallow.
            log.warn("Redis rate-limit failed, degrading to in-memory: tenant={}, tool={}, err={}",
                    tenant, toolId, e.getMessage());
            return tryAcquireInMemory(tenant, toolId, rate);
        }
    }

    /** Default-QPS convenience overload. */
    public boolean tryAcquire(String tenantId, String toolId) {
        return tryAcquire(tenantId, toolId, rateLimitProps.getDefaultQps());
    }

    /**
     * In-memory sliding 1-second window fallback.
     *
     * <p>Per-instance only — not distributed. Acceptable when Redis is down
     * (best-effort throttling, eventual consistency across instances).</p>
     */
    private boolean tryAcquireInMemory(String tenantId, String toolId, int rate) {
        String key = tenantId + ":" + toolId;
        long now = System.currentTimeMillis();
        long windowStart = now / 1000 * 1000;

        long[] window = inMemoryWindows.computeIfAbsent(key, k -> new long[]{windowStart, 0});
        synchronized (window) {
            if (window[0] != windowStart) {
                window[0] = windowStart;
                window[1] = 0;
            }
            if (window[1] >= rate) {
                log.debug("rate limited (in-memory): tenant={}, tool={}, count={}, rate={}",
                        tenantId, toolId, window[1], rate);
                return false;
            }
            window[1]++;
            return true;
        }
    }

    /** Test helper: reset in-memory counters. */
    public void resetInMemory() {
        inMemoryWindows.clear();
    }

    /** Test helper: current in-memory count for a key (0 if absent). */
    public long getInMemoryCount(String tenantId, String toolId) {
        long[] window = inMemoryWindows.get(tenantId + ":" + toolId);
        return window == null ? 0 : window[1];
    }
}
