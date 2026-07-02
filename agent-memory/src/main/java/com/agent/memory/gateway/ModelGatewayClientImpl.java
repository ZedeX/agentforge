package com.agent.memory.gateway;

import agentplatform.model.v1.ChatRequest;
import agentplatform.model.v1.ChatResponse;
import agentplatform.model.v1.Message;
import agentplatform.model.v1.ModelGatewayGrpc;
import com.agent.memory.config.MemoryProperties;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Model Gateway gRPC client implementation (Plan 03 T4).
 *
 * <p>Wires {@link ModelGatewayGrpc.ModelGatewayBlockingStub} via
 * {@code @GrpcClient("model-gateway")} (defined in application.yml
 * {@code grpc.client.model-gateway.address}) and calls the
 * {@code Chat(ChatRequest)} RPC with {@code scene=summary} for memory distillation.
 *
 * <p>Conditional on {@code memory.model-gateway.enabled=true} (default true in
 * production, false in test {@code application-test.yml} to avoid gRPC channel
 * initialization during unit tests).
 *
 * <p>This is the first real model-gateway gRPC client in the project
 * (agent-runtime / agent-task-orchestrator only declare the starter dep but
 * have not wired stubs yet).
 */
@Component
@ConditionalOnProperty(prefix = "memory.model-gateway", name = "enabled", havingValue = "true")
public class ModelGatewayClientImpl implements ModelGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(ModelGatewayClientImpl.class);

    private final ModelGatewayGrpc.ModelGatewayBlockingStub stub;
    private final MemoryProperties properties;

    public ModelGatewayClientImpl(
            @GrpcClient("model-gateway") ModelGatewayGrpc.ModelGatewayBlockingStub stub,
            MemoryProperties properties) {
        this.stub = stub;
        this.properties = properties;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        String callId = UUID.randomUUID().toString();
        String scene = properties.getModelGateway().getDistillScene();
        String tier = properties.getModelGateway().getDistillTier();

        ChatRequest request = ChatRequest.newBuilder()
                .setCallId(callId)
                .setScene(scene)
                .setTier(tier)
                .addMessages(Message.newBuilder()
                        .setRole("system")
                        .setContent(systemPrompt)
                        .build())
                .addMessages(Message.newBuilder()
                        .setRole("user")
                        .setContent(userPrompt)
                        .build())
                .build();

        log.debug("调用模型网关蒸馏 callId={} scene={} tier={}", callId, scene, tier);
        ChatResponse response = stub.chat(request);

        String content = response.getContent();
        log.info("模型网关蒸馏返回 callId={} model={} inputTokens={} outputTokens={} contentLen={}",
                callId, response.getModel(), response.getInputTokens(),
                response.getOutputTokens(), content == null ? 0 : content.length());
        return content;
    }
}
