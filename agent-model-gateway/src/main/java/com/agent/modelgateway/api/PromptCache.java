package com.agent.modelgateway.api;

import com.agent.modelgateway.model.ChatReply;

/**
 * Prompt cache (doc 02-api §5, PRD §二(二)2 caching).
 *
 * <p>Redis Hash keyed by tenantId + md5(prompt prefix 256 chars), TTL 24h.
 * Skeleton stage: ConcurrentHashMap + TTL. Redis deferred to Plan 07 T11.</p>
 */
public interface PromptCache {

    /**
     * Look up cached response for the given prompt prefix.
     *
     * @param tenantId tenant identifier
     * @param prompt   full prompt content
     * @return cached ChatReply if hit, null if miss
     */
    ChatReply lookup(String tenantId, String prompt);

    /**
     * Store a response in cache.
     *
     * @param tenantId tenant identifier
     * @param prompt   full prompt content
     * @param reply    response to cache
     */
    void put(String tenantId, String prompt, ChatReply reply);
}
