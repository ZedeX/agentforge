package com.agent.runtime.api;

/**
 * Model gateway client stub (F6 think phase: generate thought / tool_call / final_answer).
 * Real implementation should route to agent-model-gateway module.
 */
public interface ModelGatewayClient {

    /**
     * Chat with model to generate output.
     *
     * @param prompt input prompt
     * @return model output (thought / tool_call(toolId, args) / final_answer)
     */
    String chat(String prompt);
}
