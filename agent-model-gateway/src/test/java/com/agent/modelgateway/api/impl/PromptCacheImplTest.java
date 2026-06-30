package com.agent.modelgateway.api.impl;

import com.agent.modelgateway.model.ChatReply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PromptCacheImpl unit tests (doc 02-api §5).
 */
@DisplayName("PromptCacheImpl 提示缓存")
class PromptCacheImplTest {

    private final PromptCacheImpl cache = new PromptCacheImpl();

    @Test
    @DisplayName("lookup 未命中返回 null")
    void should_ReturnNull_When_CacheMiss() {
        assertThat(cache.lookup("tenant-1", "some prompt")).isNull();
    }

    @Test
    @DisplayName("put 后 lookup 命中, cacheHit 标记为 true")
    void should_HitAndMarkCacheHit_When_PutThenLookup() {
        ChatReply reply = new ChatReply("openai", "gpt-4o", "cached response", 10, 5, 100, false);
        cache.put("tenant-1", "Hello, world", reply);

        ChatReply hit = cache.lookup("tenant-1", "Hello, world");
        assertThat(hit).isNotNull();
        assertThat(hit.getContent()).isEqualTo("cached response");
        assertThat(hit.isCacheHit()).isTrue();
    }

    @Test
    @DisplayName("相同前缀 256 字符的不同 prompt 共享缓存键")
    void should_ShareKey_When_PromptPrefixSame256Chars() {
        String prefix = "a".repeat(300);
        ChatReply reply = new ChatReply("openai", "gpt-4o", "response", 10, 5, 100, false);
        cache.put("tenant-1", prefix + "X", reply);

        // Same first 256 chars → cache hit
        ChatReply hit = cache.lookup("tenant-1", prefix + "Y");
        assertThat(hit).isNotNull();
        assertThat(hit.getContent()).isEqualTo("response");
    }

    @Test
    @DisplayName("null 或空 prompt 安全跳过")
    void should_Skip_When_PromptNullOrEmpty() {
        cache.put("tenant-1", null, new ChatReply("openai", "gpt-4o", "x", 1, 1, 1, false));
        cache.put("tenant-1", "", new ChatReply("openai", "gpt-4o", "x", 1, 1, 1, false));
        assertThat(cache.lookup("tenant-1", null)).isNull();
        assertThat(cache.lookup("tenant-1", "")).isNull();
    }

    @Test
    @DisplayName("不同 tenantId 缓存隔离, 互不命中")
    void should_IsolateByTenant_When_DifferentTenantIds() {
        ChatReply reply = new ChatReply("openai", "gpt-4o", "shared", 10, 5, 100, false);
        cache.put("tenant-A", "same prompt", reply);
        // tenant-B should not see tenant-A's cache
        assertThat(cache.lookup("tenant-B", "same prompt")).isNull();
        assertThat(cache.lookup("tenant-A", "same prompt")).isNotNull();
    }

    @Test
    @DisplayName("null reply 不写入缓存")
    void should_SkipPut_When_ReplyNull() {
        cache.put("tenant-1", "prompt", null);
        assertThat(cache.lookup("tenant-1", "prompt")).isNull();
    }

    @Test
    @DisplayName("null tenantId 安全跳过 put 和 lookup")
    void should_Skip_When_TenantIdNull() {
        cache.put(null, "prompt", new ChatReply("openai", "gpt-4o", "x", 1, 1, 1, false));
        assertThat(cache.lookup(null, "prompt")).isNull();
    }
}
