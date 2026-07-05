package com.agent.runtime.api.impl;

import com.agent.runtime.api.MemoryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * No-op memory client fallback (T3).
 *
 * <p>Returns empty string when {@code runtime.memory-client.enabled=false}
 * (default / test env). ReActLoop can call recallMemory without null check.
 * When T6+ adds real gRPC MemoryClient impl (enabled=true), this bean is
 * NOT created and the real impl takes over.</p>
 */
@Component
@ConditionalOnProperty(name = "runtime.memory-client.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpMemoryClient implements MemoryClient {

    private static final Logger log = LoggerFactory.getLogger(NoOpMemoryClient.class);

    @Override
    public String recallMemory(String agentInstanceId, String query) {
        log.debug("NoOpMemoryClient recallMemory: agentInstanceId={}, queryLen={}",
                agentInstanceId, query == null ? 0 : query.length());
        return "";
    }
}
