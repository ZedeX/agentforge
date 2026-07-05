package com.agent.tool.engine.config;

import io.grpc.Channel;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * agent-memory gRPC 客户端配置（doc 05 §8 ToolSemanticRecaller）。
 *
 * <p>条件装配：{@code tool.memory-client.enabled=true} 时激活。
 * 测试环境关闭以避免连接 agent-memory:9088。</p>
 *
 * <p>提供 {@link agentplatform.memory.v1.MemoryServiceGrpc.MemoryServiceBlockingStub}
 * 和 {@link agentplatform.memory.v1.MemoryServiceGrpc.MemoryServiceFutureStub}，
 * 由 grpc-client-spring-boot-starter 自动注入 channel。</p>
 */
@Configuration
@ConditionalOnProperty(name = "tool.memory-client.enabled", havingValue = "true")
public class MemoryClientConfig {

    @Bean
    public agentplatform.memory.v1.MemoryServiceGrpc.MemoryServiceBlockingStub memoryServiceBlockingStub(
            @GrpcClient("memory-service") Channel channel) {
        return agentplatform.memory.v1.MemoryServiceGrpc.newBlockingStub(channel);
    }

    @Bean
    public agentplatform.memory.v1.MemoryServiceGrpc.MemoryServiceFutureStub memoryServiceFutureStub(
            @GrpcClient("memory-service") Channel channel) {
        return agentplatform.memory.v1.MemoryServiceGrpc.newFutureStub(channel);
    }
}
