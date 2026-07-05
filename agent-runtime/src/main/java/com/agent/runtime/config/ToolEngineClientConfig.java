package com.agent.runtime.config;

import io.grpc.Channel;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * agent-tool-engine gRPC 客户端配置（doc 06 §8.2）。
 *
 * <p>条件装配：{@code runtime.tool-engine-client.enabled=true} 时激活。
 * 测试环境关闭以避免连接 agent-tool-engine:9090。</p>
 *
 * <p>提供 {@link agentplatform.tool.v1.ToolGatewayGrpc.ToolGatewayBlockingStub}
 * 和 {@link agentplatform.tool.v1.ToolGatewayGrpc.ToolGatewayFutureStub}，
 * 由 grpc-client-spring-boot-starter 自动注入 channel（@GrpcClient("tool-engine")）。</p>
 */
@Configuration
@ConditionalOnProperty(name = "runtime.tool-engine-client.enabled", havingValue = "true")
public class ToolEngineClientConfig {

    @Bean
    public agentplatform.tool.v1.ToolGatewayGrpc.ToolGatewayBlockingStub toolGatewayBlockingStub(
            @GrpcClient("tool-engine") Channel channel) {
        return agentplatform.tool.v1.ToolGatewayGrpc.newBlockingStub(channel);
    }

    @Bean
    public agentplatform.tool.v1.ToolGatewayGrpc.ToolGatewayFutureStub toolGatewayFutureStub(
            @GrpcClient("tool-engine") Channel channel) {
        return agentplatform.tool.v1.ToolGatewayGrpc.newFutureStub(channel);
    }
}
