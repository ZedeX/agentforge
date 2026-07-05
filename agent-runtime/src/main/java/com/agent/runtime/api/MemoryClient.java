package com.agent.runtime.api;

/**
 * Memory service client for recalling historical experience (doc 06-runtime §2, F6 think phase).
 *
 * <p>T3 minimal interface: only {@link #recallMemory} is required to inject
 * distilled memory into ReAct prompt before Think phase. Real gRPC adapter
 * will be wired in T6+ via {@code MemoryServiceGrpc.MemoryServiceBlockingStub}.</p>
 */
public interface MemoryClient {

    /**
     * Recall relevant memory snippets for given query.
     *
     * @param agentInstanceId agent instance id (used as memory scope key)
     * @param query           natural language query (typically user input or current step description)
     * @return recalled memory text (may be empty when no match or client disabled); never null
     */
    String recallMemory(String agentInstanceId, String query);
}
