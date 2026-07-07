package com.agent.runtime.config;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Resilience4j 配置（doc 06 §6：熔断器 + 重试器 + 隔离仓 + 超时）。
 *
 * <p>S-02: 补全 Bulkhead + TimeLimiter + Retry 异常过滤。
 *
 * <p>提供两个熔断器：
 * <ul>
 *   <li>{@code model-gateway}：连续 {@code runtime.circuit.model.failure-threshold} 次失败 → 熔断 30s</li>
 *   <li>{@code tool-engine}：连续 {@code runtime.circuit.tool.failure-threshold} 次失败 → 熔断 30s</li>
 * </ul>
 *
 * <p>提供一个重试器 {@code default}：
 * {@code runtime.retry.max-attempts} 次指数退避，初始 {@code initial-backoff-ms}，倍数 {@code multiplier}。
 * 仅重试可恢复异常（超时、IO 异常），不重试业务异常。</p>
 *
 * <p>提供两个隔离仓（S-02）：
 * <ul>
 *   <li>{@code model-gateway}：最大并发 {@code runtime.bulkhead.max-concurrent} 个调用</li>
 *   <li>{@code tool-engine}：最大并发 {@code runtime.bulkhead.max-concurrent} 个调用</li>
 * </ul>
 *
 * <p>提供两个超时器（S-02）：
 * <ul>
 *   <li>{@code model-gateway}：超时 {@code runtime.time-limiter.timeout-ms} ms</li>
 *   <li>{@code tool-engine}：超时 {@code runtime.time-limiter.timeout-ms} ms</li>
 * </ul>
 */
@Configuration
public class Resilience4jConfig {

    /** 熔断器名称：ModelGateway */
    public static final String CB_MODEL_GATEWAY = "model-gateway";
    /** 熔断器名称：ToolEngine */
    public static final String CB_TOOL_ENGINE = "tool-engine";
    /** 隔离仓名称：ModelGateway */
    public static final String BH_MODEL_GATEWAY = "model-gateway";
    /** 隔离仓名称：ToolEngine */
    public static final String BH_TOOL_ENGINE = "tool-engine";
    /** 超时器名称：ModelGateway */
    public static final String TL_MODEL_GATEWAY = "model-gateway";
    /** 超时器名称：ToolEngine */
    public static final String TL_TOOL_ENGINE = "tool-engine";
    /** 重试器名称：default */
    public static final String RETRY_DEFAULT = "default";
    /** 重试器配置名称（不能用 'default'，保留字） */
    private static final String RETRY_CONFIG = "retry-config";

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

        // Resilience4j 2.x: 先创建默认 Registry，再注册配置
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        registry.addConfiguration(CB_MODEL_GATEWAY, modelConfig);
        registry.addConfiguration(CB_TOOL_ENGINE, toolConfig);
        // 预创建实例：用同名配置创建同名实例
        registry.circuitBreaker(CB_MODEL_GATEWAY, CB_MODEL_GATEWAY);
        registry.circuitBreaker(CB_TOOL_ENGINE, CB_TOOL_ENGINE);
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
                // S-02: 仅重试可恢复异常，不重试业务异常
                .retryExceptions(TimeoutException.class,
                        java.io.IOException.class,
                        java.util.concurrent.ExecutionException.class)
                .ignoreExceptions(IllegalArgumentException.class,
                        IllegalStateException.class)
                .build();

        // Resilience4j 2.x: 先创建默认 Registry，再注册配置
        RetryRegistry registry = RetryRegistry.ofDefaults();
        registry.addConfiguration(RETRY_CONFIG, config);
        // 预创建实例：实例名 default，配置名 retry-config
        registry.retry(RETRY_DEFAULT, RETRY_CONFIG);
        return registry;
    }

    /**
     * S-02: Bulkhead 隔离仓 — 限制对 model-gateway / tool-engine 的并发调用数。
     * 防止下游慢依赖耗尽线程池。
     */
    @Bean
    public BulkheadRegistry bulkheadRegistry() {
        int maxConcurrent = properties.getBulkhead() != null
                ? properties.getBulkhead().getMaxConcurrent() : 20;
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(maxConcurrent)
                .maxWaitDuration(Duration.ofMillis(100))
                .build();

        BulkheadRegistry registry = BulkheadRegistry.ofDefaults();
        registry.addConfiguration(BH_MODEL_GATEWAY, config);
        registry.addConfiguration(BH_TOOL_ENGINE, config);
        registry.bulkhead(BH_MODEL_GATEWAY, BH_MODEL_GATEWAY);
        registry.bulkhead(BH_TOOL_ENGINE, BH_TOOL_ENGINE);
        return registry;
    }

    /**
     * S-02: TimeLimiter 超时器 — 为 model-gateway / tool-engine 调用设置上限超时。
     * 与 gRPC deadline 互补，覆盖非 gRPC 调用路径。
     */
    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        long timeoutMs = properties.getTimeLimiter() != null
                ? properties.getTimeLimiter().getTimeoutMs() : 30000;
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(timeoutMs))
                .cancelRunningFuture(true)
                .build();

        TimeLimiterRegistry registry = TimeLimiterRegistry.ofDefaults();
        registry.addConfiguration(TL_MODEL_GATEWAY, config);
        registry.addConfiguration(TL_TOOL_ENGINE, config);
        registry.timeLimiter(TL_MODEL_GATEWAY, TL_MODEL_GATEWAY);
        registry.timeLimiter(TL_TOOL_ENGINE, TL_TOOL_ENGINE);
        return registry;
    }
}
