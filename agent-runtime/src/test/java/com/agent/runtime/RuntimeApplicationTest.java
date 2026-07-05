package com.agent.runtime;

import com.agent.runtime.config.RuntimeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1 骨架补全验证：Spring Context 加载测试（doc 06 T1）。
 *
 * <p>验证项：
 * <ul>
 *   <li>Spring Context 加载成功（pom 依赖 / yml / 启动类 / 配置类 全部就位）</li>
 *   <li>RuntimeProperties 正确绑定 application-test.yml 中的 {@code runtime.*} 配置</li>
 *   <li>3 个 gRPC client config 在测试环境（enabled=false）不创建 stub bean</li>
 *   <li>Resilience4j CircuitBreakerRegistry / RetryRegistry bean 创建成功</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class RuntimeApplicationTest {

    @Autowired
    private RuntimeProperties properties;

    @Autowired
    private io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private io.github.resilience4j.retry.RetryRegistry retryRegistry;

    @Test
    void contextLoads() {
        // Spring Context 加载成功即通过
    }

    @Test
    void runtimePropertiesAreBound() {
        assertThat(properties).isNotNull();
        assertThat(properties.getReact().getMaxSteps()).isEqualTo(20);
        assertThat(properties.getReact().getTokenBudget()).isEqualTo(32000);
        assertThat(properties.getReact().getTokenYellowThreshold()).isEqualTo(0.6);
        assertThat(properties.getReact().getTokenRedThreshold()).isEqualTo(0.8);
        assertThat(properties.getReflexion().getInterval()).isEqualTo(3);
        assertThat(properties.getCircuit().getModel().getFailureThreshold()).isEqualTo(5);
        assertThat(properties.getCircuit().getTool().getFailureThreshold()).isEqualTo(3);
        assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(3);
        assertThat(properties.getRetry().getInitialBackoffMs()).isEqualTo(200);
        assertThat(properties.getRetry().getMultiplier()).isEqualTo(3.0);
    }

    @Test
    void gRPCClientsAreDisabledInTest() {
        // 测试环境所有 gRPC client 应关闭（避免实际连接）
        assertThat(properties.getModelGatewayClient().isEnabled()).isFalse();
        assertThat(properties.getToolEngineClient().isEnabled()).isFalse();
        assertThat(properties.getMemoryClient().isEnabled()).isFalse();
    }

    @Test
    void resilience4jRegistriesAreCreated() {
        assertThat(circuitBreakerRegistry).isNotNull();
        assertThat(circuitBreakerRegistry.find("model-gateway")).isPresent();
        assertThat(circuitBreakerRegistry.find("tool-engine")).isPresent();
        assertThat(retryRegistry).isNotNull();
        assertThat(retryRegistry.find("default")).isPresent();
    }
}
