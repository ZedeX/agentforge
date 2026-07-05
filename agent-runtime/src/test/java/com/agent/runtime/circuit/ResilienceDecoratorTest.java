package com.agent.runtime.circuit;

import com.agent.runtime.config.Resilience4jConfig;
import com.agent.runtime.config.RuntimeProperties;
import com.agent.runtime.exception.CircuitOpenException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ResilienceDecorator unit tests (T9, doc 06 §6).
 */
class ResilienceDecoratorTest {

    // ============ Circuit breaker opening ============

    @Nested
    @DisplayName("circuit breaker opens after failure threshold")
    class CircuitOpenTests {

        private ResilienceDecorator decorator;

        @BeforeEach
        void setUp() {
            RuntimeProperties props = new RuntimeProperties();
            props.getCircuit().getModel().setFailureThreshold(3);
            props.getCircuit().getModel().setOpenDurationMs(60000);
            props.getCircuit().getTool().setFailureThreshold(2);
            props.getCircuit().getTool().setOpenDurationMs(60000);
            // No retry — to test circuit breaker in isolation
            props.getRetry().setMaxAttempts(1);
            props.getRetry().setInitialBackoffMs(10);

            Resilience4jConfig config = new Resilience4jConfig(props);
            decorator = new ResilienceDecorator(
                    config.circuitBreakerRegistry(), config.retryRegistry());
        }

        @Test
        @DisplayName("model-gateway: successful call passes through")
        void modelGateway_successPassesThrough() {
            String result = decorator.decorateModelGateway(() -> "ok").get();
            assertThat(result).isEqualTo("ok");
        }

        @Test
        @DisplayName("model-gateway: circuit opens after failure threshold (3)")
        void modelGateway_circuitOpensAfterThreshold() {
            // Failure threshold = 3, no retry (maxAttempts=1)
            for (int i = 0; i < 3; i++) {
                try {
                    decorator.decorateModelGateway(() -> { throw new RuntimeException("fail"); }).get();
                } catch (RuntimeException ignored) {}
            }

            assertThat(decorator.getModelGatewayCircuitBreaker().getState())
                    .isEqualTo(CircuitBreaker.State.OPEN);

            assertThatThrownBy(() -> decorator.decorateModelGateway(() -> "ok").get())
                    .isInstanceOf(CircuitOpenException.class)
                    .hasMessageContaining("model-gateway");
        }

        @Test
        @DisplayName("model-gateway: circuit open exception contains circuit name and state")
        void modelGateway_circuitOpenDetails() {
            for (int i = 0; i < 3; i++) {
                try {
                    decorator.decorateModelGateway(() -> { throw new RuntimeException("fail"); }).get();
                } catch (RuntimeException ignored) {}
            }

            assertThatThrownBy(() -> decorator.decorateModelGateway(() -> "ok").get())
                    .isInstanceOf(CircuitOpenException.class)
                    .satisfies(ex -> {
                        CircuitOpenException coe = (CircuitOpenException) ex;
                        assertThat(coe.getCircuitName()).isEqualTo("model-gateway");
                        assertThat(coe.getCircuitState()).isEqualTo("OPEN");
                    });
        }

        @Test
        @DisplayName("tool-engine: circuit opens after failure threshold (2)")
        void toolEngine_circuitOpensAfterThreshold() {
            // Failure threshold = 2, no retry (maxAttempts=1)
            for (int i = 0; i < 2; i++) {
                try {
                    decorator.decorateToolEngine(() -> { throw new RuntimeException("fail"); }).get();
                } catch (RuntimeException ignored) {}
            }

            assertThat(decorator.getToolEngineCircuitBreaker().getState())
                    .isEqualTo(CircuitBreaker.State.OPEN);

            assertThatThrownBy(() -> decorator.decorateToolEngine(() -> "ok").get())
                    .isInstanceOf(CircuitOpenException.class)
                    .hasMessageContaining("tool-engine");
        }

        @Test
        @DisplayName("model and tool circuit breakers are independent")
        void independentCircuitBreakers() {
            // Open model-gateway circuit (3 failures)
            for (int i = 0; i < 3; i++) {
                try {
                    decorator.decorateModelGateway(() -> { throw new RuntimeException("fail"); }).get();
                } catch (RuntimeException ignored) {}
            }

            assertThat(decorator.getModelGatewayCircuitBreaker().getState())
                    .isEqualTo(CircuitBreaker.State.OPEN);
            assertThat(decorator.getToolEngineCircuitBreaker().getState())
                    .isEqualTo(CircuitBreaker.State.CLOSED);

            // Tool engine should still work
            String result = decorator.decorateToolEngine(() -> "still-works").get();
            assertThat(result).isEqualTo("still-works");
        }
    }

    // ============ Retry behavior ============

    @Nested
    @DisplayName("retry behavior")
    class RetryTests {

        private ResilienceDecorator decorator;

        @BeforeEach
        void setUp() {
            RuntimeProperties props = new RuntimeProperties();
            // High failure threshold to avoid circuit opening during retry test
            props.getCircuit().getModel().setFailureThreshold(100);
            props.getCircuit().getTool().setFailureThreshold(100);
            props.getRetry().setMaxAttempts(3);
            props.getRetry().setInitialBackoffMs(10);
            props.getRetry().setMultiplier(2.0);

            Resilience4jConfig config = new Resilience4jConfig(props);
            decorator = new ResilienceDecorator(
                    config.circuitBreakerRegistry(), config.retryRegistry());
        }

        @Test
        @DisplayName("succeeds on second attempt after transient failure")
        void retrySucceedsOnSecondAttempt() {
            AtomicInteger counter = new AtomicInteger(0);

            String result = decorator.decorateModelGateway(() -> {
                if (counter.incrementAndGet() < 2) {
                    throw new RuntimeException("transient");
                }
                return "recovered";
            }).get();

            assertThat(result).isEqualTo("recovered");
            assertThat(counter.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("exhausts max attempts and throws")
        void retryExhaustsMaxAttempts() {
            AtomicInteger counter = new AtomicInteger(0);

            assertThatThrownBy(() -> decorator.decorateModelGateway(() -> {
                counter.incrementAndGet();
                throw new RuntimeException("always fail");
            }).get()).isInstanceOf(RuntimeException.class);

            // 3 attempts total (1 initial + 2 retries)
            assertThat(counter.get()).isEqualTo(3);
        }
    }

    // ============ Circuit breaker state inspection ============

    @Nested
    @DisplayName("circuit breaker state inspection")
    class StateInspectionTests {

        private ResilienceDecorator decorator;

        @BeforeEach
        void setUp() {
            RuntimeProperties props = new RuntimeProperties();
            props.getRetry().setMaxAttempts(1);
            props.getRetry().setInitialBackoffMs(10);
            Resilience4jConfig config = new Resilience4jConfig(props);
            decorator = new ResilienceDecorator(
                    config.circuitBreakerRegistry(), config.retryRegistry());
        }

        @Test
        @DisplayName("getModelGatewayCircuitBreaker returns correct instance")
        void modelGatewayInstance() {
            CircuitBreaker cb = decorator.getModelGatewayCircuitBreaker();
            assertThat(cb).isNotNull();
            assertThat(cb.getName()).isEqualTo(Resilience4jConfig.CB_MODEL_GATEWAY);
        }

        @Test
        @DisplayName("getToolEngineCircuitBreaker returns correct instance")
        void toolEngineInstance() {
            CircuitBreaker cb = decorator.getToolEngineCircuitBreaker();
            assertThat(cb).isNotNull();
            assertThat(cb.getName()).isEqualTo(Resilience4jConfig.CB_TOOL_ENGINE);
        }
    }

    // ============ CircuitOpenException ============

    @Nested
    @DisplayName("CircuitOpenException")
    class CircuitOpenExceptionTests {

        @Test
        @DisplayName("legacy constructor preserves loopCount")
        void legacyConstructor() {
            CircuitOpenException ex = new CircuitOpenException(15);
            assertThat(ex.getLoopCount()).isEqualTo(15);
            assertThat(ex.getCircuitName()).isEqualTo("react-loop");
            assertThat(ex.getCircuitState()).isEqualTo("OPEN");
            assertThat(ex.getMessage()).contains("loop_count=15");
        }

        @Test
        @DisplayName("resilience4j constructor contains circuit name and state")
        void resilience4jConstructor() {
            RuntimeException cause = new RuntimeException("call not permitted");
            CircuitOpenException ex = new CircuitOpenException("model-gateway", "OPEN", cause);
            assertThat(ex.getCircuitName()).isEqualTo("model-gateway");
            assertThat(ex.getCircuitState()).isEqualTo("OPEN");
            assertThat(ex.getLoopCount()).isEqualTo(0);
            assertThat(ex.getMessage()).contains("model-gateway");
        }
    }
}
