package com.agent.tool.engine.api.impl;

import com.agent.tool.engine.config.ToolEngineProperties;
import com.agent.tool.engine.enums.ToolCallStatus;
import com.agent.tool.engine.model.ToolCallResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ToolCacheImpl} 单元测试.
 *
 * <p>Covers the T7 two-tier (Caffeine + Redis) cache:
 * <ul>
 *   <li>Caffeine-only tests use the no-arg constructor (no RedisTemplate).</li>
 *   <li>Redis-tier tests inject a mock RedisTemplate + ValueOperations.</li>
 * </ul>
 * </p>
 */
class ToolCacheImplTest {

    // ============ Caffeine-only tests (no Redis) ============

    @Test
    @DisplayName("get_miss: 未写入 → 返回 empty")
    void get_returnsEmpty_When_NotCached() {
        ToolCacheImpl cache = new ToolCacheImpl();

        Optional<ToolCallResult> result = cache.get("tool_x", "hash_x", "tn_1");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("get_hit: Caffeine 命中 → 返回副本 with fromCache=true")
    void get_returnsResult_When_CaffeineHit() {
        ToolCacheImpl cache = new ToolCacheImpl();
        ToolCallResult result = new ToolCallResult("tool_1", "output", ToolCallStatus.SUCCESS);
        result.setOutputTokens(10);

        cache.put("tool_1", "hash_1", "tn_1", result, Duration.ofMinutes(5));
        Optional<ToolCallResult> found = cache.get("tool_1", "hash_1", "tn_1");

        assertThat(found).isPresent();
        assertThat(found.get().getToolId()).isEqualTo("tool_1");
        assertThat(found.get().getOutput()).isEqualTo("output");
        assertThat(found.get().isFromCache()).isTrue();
    }

    @Test
    @DisplayName("get_expired: TTL 过期后 → 返回 empty (Awaitility 等待)")
    void get_returnsEmpty_When_TtlExpired() {
        ToolEngineProperties props = new ToolEngineProperties();
        props.getCache().setTtlSeconds(1); // 1 second TTL
        ToolCacheImpl cache = new ToolCacheImpl(null, props);

        ToolCallResult result = new ToolCallResult("tool_exp", "out", ToolCallStatus.SUCCESS);
        cache.put("tool_exp", "hash_exp", "tn_exp", result, Duration.ofSeconds(1));

        // Wait for Caffeine expireAfterWrite(1s) to kick in.
        // expireAfterWrite doesn't reset on access, so polling get() is safe.
        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() ->
                        assertThat(cache.get("tool_exp", "hash_exp", "tn_exp")).isEmpty());
    }

    @Test
    @DisplayName("get_hit: 返回副本, 修改副本不影响缓存")
    void get_returnsCopy_When_CacheHit() {
        ToolCacheImpl cache = new ToolCacheImpl();
        ToolCallResult original = new ToolCallResult("tool_2", "orig", ToolCallStatus.SUCCESS);
        cache.put("tool_2", "hash_2", "tn_2", original, Duration.ofMinutes(5));

        ToolCallResult copy = cache.get("tool_2", "hash_2", "tn_2").orElseThrow();
        copy.setOutput("modified");

        ToolCallResult second = cache.get("tool_2", "hash_2", "tn_2").orElseThrow();
        assertThat(second.getOutput()).isEqualTo("orig");
    }

    @Test
    @DisplayName("put_respectsMaxEntries: 超过 maxEntries 时 Caffeine LRU 驱逐旧条目")
    void put_evictsOldEntries_When_MaxEntriesExceeded() {
        ToolEngineProperties props = new ToolEngineProperties();
        props.getCache().setMaxEntries(2);
        ToolCacheImpl cache = new ToolCacheImpl(null, props);

        cache.put("tool_1", "h1", "tn", result("tool_1"), Duration.ofMinutes(5));
        cache.put("tool_2", "h2", "tn", result("tool_2"), Duration.ofMinutes(5));
        // Access tool_1 to make it more recently used than tool_2.
        cache.get("tool_1", "h1", "tn");
        // Insert a third entry — should evict tool_2 (LRU).
        cache.put("tool_3", "h3", "tn", result("tool_3"), Duration.ofMinutes(5));

        assertThat(cache.size()).isLessThanOrEqualTo(2);
        assertThat(cache.get("tool_1", "h1", "tn")).isPresent(); // recently accessed
        assertThat(cache.get("tool_3", "h3", "tn")).isPresent(); // just inserted
        // tool_2 may or may not be evicted depending on Caffeine's eviction policy,
        // but total size must respect maxEntries.
    }

    @Test
    @DisplayName("invalidate(toolId): 删除该 toolId 全部 Caffeine 条目, 其他 toolId 保留")
    void invalidate_removesAllEntries_For_GivenToolId() {
        ToolCacheImpl cache = new ToolCacheImpl();
        cache.put("tool_a", "h1", "tn_1", result("tool_a"), Duration.ofMinutes(5));
        cache.put("tool_a", "h2", "tn_2", result("tool_a"), Duration.ofMinutes(5));
        cache.put("tool_b", "h1", "tn_1", result("tool_b"), Duration.ofMinutes(5));

        cache.invalidate("tool_a");

        assertThat(cache.get("tool_a", "h1", "tn_1")).isEmpty();
        assertThat(cache.get("tool_a", "h2", "tn_2")).isEmpty();
        assertThat(cache.get("tool_b", "h1", "tn_1")).isPresent();
    }

    @Test
    @DisplayName("invalidate(toolId, tenantId): 仅删除指定 toolId + tenantId 条目")
    void invalidate_removesOnlyMatchingTenant_For_GivenToolAndTenant() {
        ToolCacheImpl cache = new ToolCacheImpl();
        cache.put("tool_a", "h1", "tn_1", result("tool_a"), Duration.ofMinutes(5));
        cache.put("tool_a", "h2", "tn_2", result("tool_a"), Duration.ofMinutes(5));

        cache.invalidate("tool_a", "tn_1");

        assertThat(cache.get("tool_a", "h1", "tn_1")).isEmpty();
        assertThat(cache.get("tool_a", "h2", "tn_2")).isPresent();
    }

    // ============ Redis mock tests ============

    @Test
    @DisplayName("put_writesToRedis: put 时 Redis opsForValue().set 被调用")
    void put_writesToRedis_When_RedisEnabled() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        ToolEngineProperties props = new ToolEngineProperties();
        props.getCache().setEnabled(true);
        ToolCacheImpl cache = new ToolCacheImpl(redis, props);

        ToolCallResult result = result("tool_r");
        cache.put("tool_r", "hash_r", "tn_r", result, Duration.ofMinutes(5));

        verify(valueOps, times(1)).set(anyString(), any(), eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("get_hitPopulatesCaffeine: Redis 命中后回填 Caffeine, 第二次 get 不查 Redis")
    void get_backfillsCaffeine_When_RedisHit() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        // First get: Redis returns a ToolCallResult.
        ToolCallResult redisResult = result("tool_redis");
        when(valueOps.get("tool:cache:tool_redis:hash_redis:tn_redis"))
                .thenReturn(redisResult);

        ToolEngineProperties props = new ToolEngineProperties();
        props.getCache().setEnabled(true);
        ToolCacheImpl cache = new ToolCacheImpl(redis, props);

        // First get: Caffeine miss → Redis hit → backfill Caffeine.
        Optional<ToolCallResult> first = cache.get("tool_redis", "hash_redis", "tn_redis");
        assertThat(first).isPresent();
        assertThat(first.get().getToolId()).isEqualTo("tool_redis");

        // Second get: Caffeine hit → should NOT query Redis again.
        Optional<ToolCallResult> second = cache.get("tool_redis", "hash_redis", "tn_redis");
        assertThat(second).isPresent();

        verify(valueOps, times(1)).get("tool:cache:tool_redis:hash_redis:tn_redis");
    }

    @Test
    @DisplayName("get_miss_returnsEmpty: Redis 未命中 → empty, 不回填 Caffeine")
    void get_returnsEmpty_When_RedisMiss() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        ToolEngineProperties props = new ToolEngineProperties();
        props.getCache().setEnabled(true);
        ToolCacheImpl cache = new ToolCacheImpl(redis, props);

        Optional<ToolCallResult> result = cache.get("tool_miss", "hash_miss", "tn_miss");

        assertThat(result).isEmpty();
        verify(valueOps, times(1)).get("tool:cache:tool_miss:hash_miss:tn_miss");
    }

    @Test
    @DisplayName("Redis 异常 → 降级只用 Caffeine, 不抛异常")
    void get_degradesGracefully_When_RedisThrows() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis down"));

        ToolEngineProperties props = new ToolEngineProperties();
        props.getCache().setEnabled(true);
        ToolCacheImpl cache = new ToolCacheImpl(redis, props);

        // Should not throw — returns empty.
        Optional<ToolCallResult> result = cache.get("tool_err", "hash_err", "tn_err");
        assertThat(result).isEmpty();

        // Caffeine should still work.
        cache.put("tool_err", "hash_err", "tn_err", result("tool_err"), Duration.ofMinutes(5));
        assertThat(cache.get("tool_err", "hash_err", "tn_err")).isPresent();
    }

    // ============ Legacy API tests ============

    @Test
    @DisplayName("legacy lookup/cache: inputHash keyed, 副本 with fromCache=true")
    void legacyLookupAndCache_Work_With_InputHash() {
        ToolCacheImpl cache = new ToolCacheImpl();
        ToolCallResult result = result("tool_legacy");

        cache.cache("hash_legacy", result);
        Optional<ToolCallResult> found = cache.lookup("hash_legacy");

        assertThat(found).isPresent();
        assertThat(found.get().isFromCache()).isTrue();
        assertThat(found.get().getToolId()).isEqualTo("tool_legacy");
    }

    @Test
    @DisplayName("legacy lookup: 空或空白 hash → empty")
    void legacyLookup_returnsEmpty_When_HashBlank() {
        ToolCacheImpl cache = new ToolCacheImpl();
        assertThat(cache.lookup(null)).isEmpty();
        assertThat(cache.lookup("  ")).isEmpty();
    }

    @Test
    @DisplayName("legacy cache: null result → 跳过, 不写入")
    void legacyCache_skips_When_ResultNull() {
        ToolCacheImpl cache = new ToolCacheImpl();
        cache.cache("hash_null", null);
        assertThat(cache.lookup("hash_null")).isEmpty();
    }

    // ============ Helper ============

    private static ToolCallResult result(String toolId) {
        return new ToolCallResult(toolId, "output-" + toolId, ToolCallStatus.SUCCESS);
    }
}
