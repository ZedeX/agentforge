package com.agent.runtime.config;

import io.grpc.Channel;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * agent-model-gateway gRPC 客户端配置（doc 06 §8.1）。
 *
 * <p>条件装配：{@code runtime.model-gateway-client.enabled=true} 时激活。
 * 测试环境关闭以避免连接 agent-model-gateway:9094。</p>
 *
 * <p>提供 {@link agentplatform.model.v1.ModelGatewayGrpc.ModelGatewayBlockingStub}
 * 和 {@link agentplatform.model.v1.ModelGatewayGrpc.ModelGatewayFutureStub}，
 * 由 grpc-client-spring-boot-starter 自动注入 channel（@GrpcClient("model-gateway")）。</p>
 */
@Configuration
@ConditionalOnProperty(name = "runtime.model-gateway-client.enabled", havingValue = "true")
public class ModelGatewayClientConfig {

    @Bean
    public agentplatform.model.v1.ModelGatewayGrpc.ModelGatewayBlockingStub modelGatewayBlockingStub(
            @GrpcClient("model-gateway") Channel channel) {
        return agentplatform.model.v1.ModelGatewayGrpc.newBlockingStub(channel);
    }

    @Bean
    public agentplatform.model.v1.ModelGatewayGrpc.ModelGatewayFutureStub modelGatewayFutureStub(
            @GrpcClient("model-gateway") Channel channel) {
        return agentplatform.model.v1.ModelGatewayGrpc.newFutureStub(channel);
    }
}
