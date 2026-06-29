package com.agent.tool.engine.api;

import com.agent.tool.engine.model.ToolCallResult;

import java.util.Optional;

/**
 * Tool result cache port (F8 cache branch: input-hash keyed).
 */
public interface ToolCache {

    /**
     * Lookup cached result by input hash.
     */
    Optional<ToolCallResult> lookup(String inputHash);

    /**
     * Cache a result by input hash.
     */
    void cache(String inputHash, ToolCallResult result);
}
