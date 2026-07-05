package com.agent.runtime.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j 配置（doc 06 §6：熔断器 + 重试器）。
 *
 * <p>提供两个熔断器：
 * <ul>
 *   <li>{@code model-gateway}：连续 {@code runtime.circuit.model.failure-threshold} 次失败 → 熔断 30s</li>
 *   <li>{@code tool-engine}：连续 {@code runtime.circuit.tool.failure-threshold} 次失败 → 熔断 30s</li>
 * </ul>
 *
 * <p>提供一个重试器 {@code default}：
 * {@code runtime.retry.max-attempts} 次指数退避，初始 {@code initial-backoff-ms}，倍数 {@code multiplier}。</p>
 */
@Configuration
public class Resilience4jConfig {

    /** 熔断器名称：ModelGateway */
    public static final String CB_MODEL_GATEWAY = "model-gateway";
    /** 熔断器名称：ToolEngine */
    public static final String CB_TOOL_ENGINE = "tool-engine";
    /** 重试器名称：default */
    public static final String RETRY_DEFAULT = "default";

    private final RuntimeProperties properties;

    public Resilience4jConfig(RuntimeProperties properties) {
        this.properties = properties;
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        RuntimeProperties.Circuit.ModelCircuit modelCfg = properties.getCircuit().getModel();
        RuntimeProperties.Circuit.ToolCircuit toolCfg = properties.getCircuit().getTool();

        CircuitBreakerConfig modelConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(100f) // 失败率 100% 触发（按次数控制）
                .slidingWindowSize(modelCfg.getFailureThreshold())
                .minimumNumberOfCalls(modelCfg.getFailureThreshold())
                .waitDurationInOpenState(Duration.ofMillis(modelCfg.getOpenDurationMs()))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();

        CircuitBreakerConfig toolConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(100f)
                .slidingWindowSize(toolCfg.getFailureThreshold())
                .minimumNumberOfCalls(toolCfg.getFailureThreshold())
                .waitDurationInOpenState(Duration.ofMillis(toolCfg.getOpenDurationMs()))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();

        // Resilience4j 2.x: 用 Map 构造 Registry，自动注册所有配置
        java.util.Map<String, CircuitBreakerConfig> configs = new java.util.HashMap<>();
        configs.put(CB_MODEL_GATEWAY, modelConfig);
        configs.put(CB_TOOL_ENGINE, toolConfig);
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(configs);
        // 预创建实例，使后续 registry.find(name) 可用（Resilience4j 2.x find() 只返回已创建的实例）
        registry.circuitBreaker(CB_MODEL_GATEWAY);
        registry.circuitBreaker(CB_TOOL_ENGINE);
        return registry;
    }

    @Bean
    public RetryRegistry retryRegistry() {
        RuntimeProperties.Retry retryCfg = properties.getRetry();
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(retryCfg.getMaxAttempts())
                // Resilience4j 2.x: waitDuration 和 intervalFunction 不能同时设置
                // 用 intervalFunction 实现指数退避（initialBackoffMs * multiplier^n）
                .intervalFunction(
                        io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff(
                                retryCfg.getInitialBackoffMs(),
                                retryCfg.getMultiplier()))
                .build();

        // Resilience4j 2.x: 用 Map 构造 Registry
        java.util.Map<String, RetryConfig> configs = new java.util.HashMap<>();
        configs.put(RETRY_DEFAULT, config);
        RetryRegistry registry = RetryRegistry.of(configs);
        // 预创建实例，使后续 registry.find(name) 可用
        registry.retry(RETRY_DEFAULT);
        return registry;
    }
}
