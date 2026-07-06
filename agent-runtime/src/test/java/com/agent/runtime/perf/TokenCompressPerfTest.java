package com.agent.runtime.perf;

import com.agent.runtime.watermark.TokenBudgetCalculator;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH microbenchmark for {@link TokenBudgetCalculator#estimateTokens(String)}.
 *
 * <p>Target (docs/tests/test-plan.md): Token compress &lt; 200ms/500ms/1s. Token estimation is
 * the hot path of the token-watermark check on every ReAct step.
 *
 * <p>Run with: {@code mvn -Pperf -pl agent-runtime clean test}
 * Results: {@code agent-runtime/target/jmh-results/*.json}
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Benchmark)
public class TokenCompressPerfTest {

    /** Text length in characters. */
    @Param({"100", "1000", "10000"})
    public int textLength;

    /** Fraction of characters that are CJK (0.0 = pure ASCII, 1.0 = pure CJK). */
    @Param({"0.0", "0.5", "1.0"})
    public double cjkRatio;

    private TokenBudgetCalculator calculator;
    private String text;

    @Setup
    public void setup() {
        calculator = new TokenBudgetCalculator();
        int cjkCount = (int) (textLength * cjkRatio);
        StringBuilder sb = new StringBuilder(textLength + 1);
        for (int i = 0; i < textLength; i++) {
            if (i < cjkCount) {
                // CJK Unified Ideograph U+4E00..U+4E63 — BMP, no surrogates.
                sb.append((char) (0x4E00 + (i % 100)));
            } else {
                sb.append('a' + (i % 26));
            }
        }
        text = sb.toString();
    }

    @Benchmark
    public void estimateTokensString(Blackhole bh) {
        bh.consume(calculator.estimateTokens(text));
    }

    @Benchmark
    public void usageRatio(Blackhole bh) {
        bh.consume(calculator.usageRatio(textLength * 2L, textLength * 10L));
    }

    @Benchmark
    public void remaining(Blackhole bh) {
        bh.consume(calculator.remaining(textLength * 2L, textLength * 10L));
    }
}
