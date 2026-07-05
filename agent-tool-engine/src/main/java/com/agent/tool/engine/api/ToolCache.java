package com.agent.tool.engine.api;

import com.agent.tool.engine.model.ToolCallResult;

import java.time.Duration;
import java.util.Optional;

/**
 * Tool result cache port (F8 cache branch: two-tier Redis + Caffeine).
 *
 * <p>T6+ API: {@link #get}, {@link #put}, {@link #invalidate} use
 * {@code (toolId, paramsHash, tenantId)} composite keys for deterministic
 * cache lookup across distributed instances.</p>
 *
 * <p>Legacy API: {@link #lookup(String)} / {@link #cache(String, ToolCallResult)}
 * retained for {@code ToolGatewayImpl} skeleton (T1) until T8 rewrite switches
 * the gateway to the spec-based API.</p>
 */
public interface ToolCache {

    // ============ T7 two-tier API ============

    /**
     * Lookup a cached result by {@code toolId} + {@code paramsHash} + {@code tenantId}.
     *
     * <p>Checks Caffeine (L2) first, then Redis (L1). On Redis hit, backfills
     * Caffeine. Returns empty if both tiers miss.</p>
     */
    Optional<ToolCallResult> get(String toolId, String paramsHash, String tenantId);

    /**
     * Cache a result with an explicit TTL. Writes to both Redis (L1) and
     * Caffeine (L2). Redis failures degrade gracefully (Caffeine only).
     */
    void put(String toolId, String paramsHash, String tenantId,
             ToolCallResult result, Duration ttl);

    /**
     * Invalidate all cached entries for a given toolId (across all tenants).
     * Uses Redis SCAN + DEL and clears matching Caffeine entries.
     */
    void invalidate(String toolId);

    /**
     * Invalidate all cached entries for a given toolId + tenantId.
     */
    void invalidate(String toolId, String tenantId);

    // ============ Legacy API (T1 skeleton compat) ============

    /**
     * Lookup cached result by input hash (legacy).
     *
     * @deprecated use {@link #get(String, String, String)} — kept for ToolGatewayImpl skeleton.
     */
    @Deprecated
    Optional<ToolCallResult> lookup(String inputHash);

    /**
     * Cache a result by input hash (legacy).
     *
     * @deprecated use {@link #put(String, String, String, ToolCallResult, Duration)} — kept for ToolGatewayImpl skeleton.
     */
    @Deprecated
    void cache(String inputHash, ToolCallResult result);
}
