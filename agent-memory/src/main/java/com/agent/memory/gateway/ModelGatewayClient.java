package com.agent.memory.gateway;

/**
 * Model Gateway client port (Plan 03 T4).
 *
 * <p>Abstracts the agent-model-gateway gRPC Chat RPC so that
 * {@link com.agent.memory.api.impl.MemoryDistillerImpl} can be unit-tested
 * with a mock without standing up a real gRPC channel.
 *
 * <p>Production implementation {@code ModelGatewayClientImpl} wires
 * {@code @GrpcClient("model-gateway") ModelGatewayGrpc.ModelGatewayBlockingStub}
 * and calls {@code Chat(ChatRequest)} with {@code scene=summary}.
 */
public interface ModelGatewayClient {

    /**
     * Call the model gateway Chat RPC with the given prompts.
     *
     * @param systemPrompt system prompt (role=system, defines distillation task)
     * @param userPrompt   user prompt (role=user, contains source memory contents)
     * @return distilled summary text returned by the model
     * @throws RuntimeException if the model gateway call fails (caller should
     *                          preserve source ACTIVE state on failure)
     */
    String chat(String systemPrompt, String userPrompt);
}
