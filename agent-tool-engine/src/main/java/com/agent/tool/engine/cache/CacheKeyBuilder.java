package com.agent.tool.engine.cache;

/**
 * Builds deterministic Redis / Caffeine cache keys for tool results
 * (doc 05-tool-engine §6).
 *
 * <p>Format: {@code tool:cache:{toolId}:{paramsHash}:{tenantId}}.
 * All three segments are required for a stable, collision-free key.</p>
 */
public final class CacheKeyBuilder {

    /** Key prefix shared by all tool-cache entries (used for SCAN-based invalidate). */
    public static final String KEY_PREFIX = "tool:cache:";

    private CacheKeyBuilder() {
    }

    /**
     * Build a cache key from {@code toolId}, {@code paramsHash}, and {@code tenantId}.
     *
     * @param toolId     tool identifier (non-null, non-blank)
     * @param paramsHash SHA-256 hash of canonicalized params (non-null, non-blank)
     * @param tenantId   tenant identifier (non-null, non-blank)
     * @return deterministic key {@code tool:cache:{toolId}:{paramsHash}:{tenantId}}
     */
    public static String build(String toolId, String paramsHash, String tenantId) {
        if (toolId == null || toolId.isBlank()) {
            throw new IllegalArgumentException("toolId must not be null/blank");
        }
        if (paramsHash == null || paramsHash.isBlank()) {
            throw new IllegalArgumentException("paramsHash must not be null/blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null/blank");
        }
        return KEY_PREFIX + toolId + ":" + paramsHash + ":" + tenantId;
    }

    /**
     * Build a SCAN pattern matching all cache entries for a given toolId
     * (across all tenants and param hashes).
     *
     * @return pattern {@code tool:cache:{toolId}:*}
     */
    public static String patternForTool(String toolId) {
        return KEY_PREFIX + toolId + ":*";
    }

    /**
     * Build a SCAN pattern matching all cache entries for a given toolId + tenantId
     * (across all param hashes).
     *
     * @return pattern {@code tool:cache:{toolId}:*:{tenantId}}
     */
    public static String patternForToolAndTenant(String toolId, String tenantId) {
        return KEY_PREFIX + toolId + ":*:" + tenantId;
    }
}
