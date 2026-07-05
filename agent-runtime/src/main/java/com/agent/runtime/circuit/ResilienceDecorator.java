package com.agent.runtime.circuit;

import com.agent.runtime.config.Resilience4jConfig;
import com.agent.runtime.exception.CircuitOpenException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Resilience4j circuit breaker + retry decorator (T9, doc 06 §6).
 *
 * <p>Wraps RPC calls with circuit breaker (prevents cascading failures)
 * and retry (exponential backoff on transient errors).</p>
 *
 * <p>Two circuit breaker instances:
 * <ul>
 *   <li>{@code model-gateway}: for ModelGatewayClientImpl calls</li>
 *   <li>{@code tool-engine}: for ToolEngineClientImpl calls</li>
 * </ul>
 *
 * <p>One retry instance {@code default}: 3 attempts with exponential backoff (200/600/1800 ms).</p>
 */
@Component
public class ResilienceDecorator {

    private static final Logger log = LoggerFactory.getLogger(ResilienceDecorator.class);

    private final CircuitBreakerRegistry cbRegistry;
    private final RetryRegistry retryRegistry;

    public ResilienceDecorator(CircuitBreakerRegistry cbRegistry, RetryRegistry retryRegistry) {
        this.cbRegistry = cbRegistry;
        this.retryRegistry = retryRegistry;
    }

    /**
     * Decorate a supplier with circuit breaker + retry for ModelGateway calls.
     *
     * @param supplier the RPC call to decorate
     * @param <T>      return type
     * @return decorated supplier (must be .get() to execute)
     */
    public <T> Supplier<T> decorateModelGateway(Supplier<T> supplier) {
        return decorate(Resilience4jConfig.CB_MODEL_GATEWAY, supplier);
    }

    /**
     * Decorate a supplier with circuit breaker + retry for ToolEngine calls.
     *
     * @param supplier the RPC call to decorate
     * @param <T>      return type
     * @return decorated supplier (must be .get() to execute)
     */
    public <T> Supplier<T> decorateToolEngine(Supplier<T> supplier) {
        return decorate(Resilience4jConfig.CB_TOOL_ENGINE, supplier);
    }

    /**
     * Get the ModelGateway circuit breaker (for testing / metrics).
     */
    public CircuitBreaker getModelGatewayCircuitBreaker() {
        return cbRegistry.circuitBreaker(Resilience4jConfig.CB_MODEL_GATEWAY);
    }

    /**
     * Get the ToolEngine circuit breaker (for testing / metrics).
     */
    public CircuitBreaker getToolEngineCircuitBreaker() {
        return cbRegistry.circuitBreaker(Resilience4jConfig.CB_TOOL_ENGINE);
    }

    /**
     * Core decoration: circuit breaker → retry → supplier.
     * Translates {@link CallNotPermittedException} → {@link CircuitOpenException}.
     */
    private <T> Supplier<T> decorate(String circuitBreakerName, Supplier<T> supplier) {
        CircuitBreaker cb = cbRegistry.circuitBreaker(circuitBreakerName);
        Retry retry = retryRegistry.retry(Resilience4jConfig.RETRY_DEFAULT);

        // Decorate: retry → circuit breaker → supplier
        Supplier<T> withCb = CircuitBreaker.decorateSupplier(cb, supplier);
        final Supplier<T> withRetry = Retry.decorateSupplier(retry, withCb);
        final String cbName = circuitBreakerName;

        // Wrap to translate CallNotPermittedException → CircuitOpenException
        return () -> {
            try {
                return withRetry.get();
            } catch (CallNotPermittedException ex) {
                log.warn("Circuit breaker [{}] is OPEN, rejecting call", cbName);
                throw new CircuitOpenException(cbName,
                        cb.getState().name(), ex);
            }
        };
    }
}
