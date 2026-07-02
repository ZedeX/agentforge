package com.agent.memory.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * No-op ModelGatewayClient fallback for test / local environments where
 * agent-model-gateway is not available.
 *
 * <p>Active when {@code memory.model-gateway.enabled=false} (test application-test.yml).
 * Throws {@link UnsupportedOperationException} on every call, which is caught by
 * {@link com.agent.memory.api.impl.MemoryDistillerImpl} and preserves source ACTIVE state.
 *
 * <p>This ensures that {@code MemoryDistillerImpl} can always find a
 * {@link ModelGatewayClient} bean for constructor injection, regardless of
 * whether the real gRPC client is available.
 */
@Component
@ConditionalOnProperty(prefix = "memory.model-gateway", name = "enabled",
        havingValue = "false", matchIfMissing = false)
public class NoOpModelGatewayClient implements ModelGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(NoOpModelGatewayClient.class);

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        log.warn("NoOpModelGatewayClient 被调用：模型网关未启用 systemPromptLen={} userPromptLen={}",
                systemPrompt == null ? 0 : systemPrompt.length(),
                userPrompt == null ? 0 : userPrompt.length());
        throw new UnsupportedOperationException(
                "Model gateway is not enabled. Set memory.model-gateway.enabled=true to use real gRPC client.");
    }
}
