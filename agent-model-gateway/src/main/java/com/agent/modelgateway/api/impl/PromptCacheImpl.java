package com.agent.modelgateway.api.impl;

import com.agent.modelgateway.api.PromptCache;
import com.agent.modelgateway.model.ChatReply;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory prompt cache (doc 02-api §5).
 *
 * <p>Key = tenantId + md5(prompt prefix 256 chars). TTL 24h (skeleton uses creation timestamp).
 * Skeleton stage: ConcurrentHashMap + manual TTL. Redis Hash deferred to Plan 07 T11.</p>
 */
@Component
public class PromptCacheImpl implements PromptCache {

    private static final int PREFIX_LEN = 256;
    private static final long TTL_MS = 24L * 60 * 60 * 1000; // 24h

    private static class CacheEntry {
        final ChatReply reply;
        final long createdAt;
        CacheEntry(ChatReply reply, long createdAt) {
            this.reply = reply;
            this.createdAt = createdAt;
        }
    }

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    public ChatReply lookup(String tenantId, String prompt) {
        if (tenantId == null || prompt == null || prompt.isEmpty()) {
            return null;
        }
        String key = buildKey(tenantId, prompt);
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        // TTL check
        if (System.currentTimeMillis() - entry.createdAt > TTL_MS) {
            cache.remove(key);
            return null;
        }
        // Return a cache-hit marked copy
        return new ChatReply(
                entry.reply.getProviderCode(),
                entry.reply.getModelName(),
                entry.reply.getContent(),
                entry.reply.getInputTokens(),
                entry.reply.getOutputTokens(),
                entry.reply.getLatencyMs(),
                true // cacheHit
        );
    }

    @Override
    public void put(String tenantId, String prompt, ChatReply reply) {
        if (tenantId == null || prompt == null || prompt.isEmpty() || reply == null) {
            return;
        }
        String key = buildKey(tenantId, prompt);
        cache.put(key, new CacheEntry(reply, System.currentTimeMillis()));
    }

    private String buildKey(String tenantId, String prompt) {
        String prefix = prompt.length() > PREFIX_LEN ? prompt.substring(0, PREFIX_LEN) : prompt;
        return tenantId + ":" + md5(prefix);
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // MD5 should always be available in JDK
            return Integer.toHexString(input.hashCode());
        }
    }
}
