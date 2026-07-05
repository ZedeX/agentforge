package com.agent.tool.engine.recall;

import java.util.List;

/**
 * Port interface for recalling PROCEDURAL memories from agent-memory.
 *
 * <p>Production implementation ({@code MemoryServiceClientImpl}) wraps the
 * gRPC blocking stub with a configurable timeout. When the memory service is
 * disabled ({@code tool.memory-client.enabled=false}), no bean of this type
 * is registered, and {@code ToolSemanticRecallerImpl} falls back to
 * keyword-only matching.</p>
 */
public interface MemoryServiceClient {

    /**
     * Recall up to {@code topK} memories matching the given query.
     *
     * @param tenantId tenant scope (used as session_id in the Recall RPC)
     * @param query    natural-language query built from toolId + params
     * @param topK     maximum number of memories to return
     * @return list of recalled memories (may be empty, never null)
     * @throws MemoryServiceException if the gRPC call fails (UNAVAILABLE,
     *         DEADLINE_EXCEEDED, etc.) — caller should fall back
     */
    List<RecalledMemoryDto> recallMemories(String tenantId, String query, int topK);

    /** @return {@code true} if the underlying gRPC channel is available. */
    boolean isAvailable();
}
