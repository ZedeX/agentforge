package com.agent.tool.engine.api.impl;

import com.agent.tool.engine.api.ToolCache;
import com.agent.tool.engine.cache.CacheKeyBuilder;
import com.agent.tool.engine.config.ToolEngineProperties;
import com.agent.tool.engine.model.ToolCallResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * F8 工具结果缓存实现 (doc 05-tool-engine §6): Redis 一级 + Caffeine 二级.
 *
 * <p>Two-tier strategy:
 * <ol>
 *   <li>{@code get}: Caffeine (L2) first → Redis (L1) → backfill Caffeine → miss.</li>
 *   <li>{@code put}: write to both Redis (with TTL) and Caffeine.</li>
 *   <li>{@code invalidate}: Redis SCAN + DEL matching keys; Caffeine keySet removeIf.</li>
 * </ol>
 * </p>
 *
 * <p>Caffeine uses {@code expireAfterWrite} so TTL is fixed from write time
 * (cache hits do not refresh the TTL — callers always get a result at most
 * {@code tool.cache.ttlSeconds} old). Size-based eviction is lazy; tests should
 * call {@link #cleanUp()} before asserting size.</p>
 *
 * <p>Graceful degradation: when RedisTemplate is null (tool.cache.enabled=false)
 * or Redis operations throw, the cache degrades to Caffeine-only with a warning log.
 * No cache operation ever throws — callers (ToolGateway) treat cache as best-effort.</p>
 *
 * <p>Legacy {@code lookup(inputHash)} / {@code cache(inputHash, result)} use the
 * raw {@code inputHash} as the Caffeine key (no {@code tool:cache:} prefix) so
 * they don't collide with the new composite-key API.</p>
 */
@Component
public class ToolCacheImpl implements ToolCache {

    private static final Logger log = LoggerFactory.getLogger(ToolCacheImpl.class);

    /** Legacy cache TTL fallback when no explicit TTL provided. */
    private static final long DEFAULT_TTL_MS = 5 * 60 * 1000L;

    private final Cache<String, ToolCallResult> caffeine;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ToolEngineProperties.Cache cacheProps;
    private final ObjectMapper objectMapper;

    /** Primary constructor: Redis optional (null when tool.cache.enabled=false). */
    @Autowired
    public ToolCacheImpl(@Autowired(required = false) RedisTemplate<String, Object> redisTemplate,
                         ToolEngineProperties properties) {
        this.redisTemplate = redisTemplate;
        this.cacheProps = properties.getCache();
        this.objectMapper = new ObjectMapper();
        this.caffeine = Caffeine.newBuilder()
                .maximumSize(cacheProps.getMaxEntries())
                .expireAfterWrite(Duration.ofSeconds(cacheProps.getTtlSeconds()))
                .build();
    }

    /** No-arg constructor for tests / skeleton wiring (Caffeine-only, default config). */
    public ToolCacheImpl() {
        this(null, new ToolEngineProperties());
    }

    // ============ T7 two-tier API ============

    @Override
    public Optional<ToolCallResult> get(String toolId, String paramsHash, String tenantId) {
        String key = CacheKeyBuilder.build(toolId, paramsHash, tenantId);

        // L2: Caffeine
        ToolCallResult cached = caffeine.getIfPresent(key);
        if (cached != null) {
            log.debug("cache hit (Caffeine): key={}", key);
            return Optional.of(copyWithCacheFlag(cached));
        }

        // L1: Redis
        if (redisTemplate != null && cacheProps.isEnabled()) {
            try {
                Object raw = redisTemplate.opsForValue().get(key);
                if (raw != null) {
                    ToolCallResult result = deserialize(raw);
                    if (result != null) {
                        caffeine.put(key, result); // backfill L2
                        log.debug("cache hit (Redis, backfilled Caffeine): key={}", key);
                        return Optional.of(copyWithCacheFlag(result));
                    }
                }
            } catch (Exception e) {
                log.warn("Redis get failed, degrading to Caffeine-only: key={}, err={}",
                        key, e.getMessage());
            }
        }
        log.debug("cache miss: key={}", key);
        return Optional.empty();
    }

    @Override
    public void put(String toolId, String paramsHash, String tenantId,
                    ToolCallResult result, Duration ttl) {
        if (result == null) {
            return;
        }
        String key = CacheKeyBuilder.build(toolId, paramsHash, tenantId);
        // L2: Caffeine
        caffeine.put(key, result);

        // L1: Redis
        if (redisTemplate != null && cacheProps.isEnabled()) {
            try {
                redisTemplate.opsForValue().set(key, serialize(result), ttl);
            } catch (Exception e) {
                log.warn("Redis put failed, Caffeine only: key={}, err={}",
                        key, e.getMessage());
            }
        }
        log.debug("cache put: key={}, ttl={}ms", key, ttl.toMillis());
    }

    @Override
    public void invalidate(String toolId) {
        String pattern = CacheKeyBuilder.patternForTool(toolId);
        invalidateCaffeine(pattern);
        invalidateRedis(pattern);
    }

    @Override
    public void invalidate(String toolId, String tenantId) {
        String pattern = CacheKeyBuilder.patternForToolAndTenant(toolId, tenantId);
        invalidateCaffeine(pattern);
        invalidateRedis(pattern);
    }

    // ============ Legacy API ============

    @Override
    @Deprecated
    public Optional<ToolCallResult> lookup(String inputHash) {
        if (inputHash == null || inputHash.isBlank()) {
            return Optional.empty();
        }
        ToolCallResult cached = caffeine.getIfPresent(inputHash);
        if (cached == null) {
            log.debug("缓存未命中 (legacy): hash={}", inputHash);
            return Optional.empty();
        }
        return Optional.of(copyWithCacheFlag(cached));
    }

    @Override
    @Deprecated
    public void cache(String inputHash, ToolCallResult result) {
        if (inputHash == null || inputHash.isBlank() || result == null) {
            log.warn("缓存写入参数非法 (legacy, hash={}, result={}), 跳过",
                    inputHash, result == null ? "null" : "present");
            return;
        }
        caffeine.put(inputHash, result);
        log.debug("写入缓存 (legacy): hash={}, toolId={}", inputHash, result.getToolId());
    }

    // ============ Helpers ============

    /** Remove Caffeine entries whose key matches the given glob-style pattern. */
    private void invalidateCaffeine(String pattern) {
        String regex = globToRegex(pattern);
        int before = caffeine.asMap().size();
        caffeine.asMap().keySet().removeIf(k -> k != null && k.matches(regex));
        int after = caffeine.asMap().size();
        log.debug("Caffeine invalidate: pattern={}, removed={}", pattern, before - after);
    }

    /** SCAN + DEL matching keys in Redis. */
    private void invalidateRedis(String pattern) {
        if (redisTemplate == null || !cacheProps.isEnabled()) {
            return;
        }
        try {
            Set<String> keys = scanKeys(pattern);
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Redis invalidate: pattern={}, deleted={}", pattern, keys.size());
            }
        } catch (Exception e) {
            log.warn("Redis invalidate failed: pattern={}, err={}", pattern, e.getMessage());
        }
    }

    /** Use Redis SCAN to find keys matching a glob pattern (avoids KEYS for performance). */
    @SuppressWarnings("unchecked")
    private Set<String> scanKeys(String pattern) {
        java.util.HashSet<String> keys = new java.util.HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        try (Cursor<byte[]> cursor = redisTemplate.getConnectionFactory()
                .getConnection()
                .scan(options)) {
            while (cursor.hasNext()) {
                keys.add(new String(cursor.next()));
            }
        } catch (Exception e) {
            log.warn("Redis SCAN failed: pattern={}, err={}", pattern, e.getMessage());
        }
        return keys;
    }

    /** Convert a glob pattern (with *) to a Java regex. */
    private static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append(".");
                case '.', '(', ')', '+', '|', '^', '$', '@', '%', '\\', '[', ']', '{', '}' ->
                        sb.append('\\').append(c);
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Serialize ToolCallResult to JSON string for Redis storage. */
    private String serialize(ToolCallResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("serialize failed: {}", e.getMessage());
            return null;
        }
    }

    /** Deserialize Redis value back to ToolCallResult. */
    @SuppressWarnings("unchecked")
    private ToolCallResult deserialize(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            if (raw instanceof ToolCallResult) {
                return (ToolCallResult) raw;
            }
            String json = raw instanceof String ? (String) raw : objectMapper.writeValueAsString(raw);
            return objectMapper.readValue(json, ToolCallResult.class);
        } catch (Exception e) {
            log.warn("deserialize failed: {}", e.getMessage());
            return null;
        }
    }

    /** Return a shallow copy with fromCache=true (prevents callers from mutating cache state). */
    private ToolCallResult copyWithCacheFlag(ToolCallResult src) {
        ToolCallResult copy = new ToolCallResult(src.getToolId(), src.getOutput(), src.getStatus());
        copy.setOutputTokens(src.getOutputTokens());
        copy.setErrorStack(src.getErrorStack());
        copy.setFromCache(true);
        return copy;
    }

    // ============ Monitoring helpers ============

    /** Current Caffeine entry count (for tests / monitoring). */
    public int size() {
        caffeine.cleanUp();
        return caffeine.asMap().size();
    }

    /** Force Caffeine maintenance (size-based eviction + TTL expiry cleanup). */
    public void cleanUp() {
        caffeine.cleanUp();
    }

    /** Test helper: directly inspect Caffeine for a key (bypasses Redis). */
    public boolean caffeineContains(String key) {
        return caffeine.getIfPresent(key) != null;
    }
}
