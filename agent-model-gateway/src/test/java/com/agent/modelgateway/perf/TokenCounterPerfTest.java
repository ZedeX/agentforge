package com.agent.modelgateway.perf;

import com.agent.modelgateway.api.impl.TokenCounterImpl;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH microbenchmark for {@link TokenCounterImpl#count(String)}.
 *
 * <p>Target (docs/tests/test-plan.md): Intent recognition P95 ≤ 200ms; token counting is a
 * sub-component. Also flags the per-char {@code Matcher} allocation hotspot in {@code isCjk(char)}
 * noted during the JMH feasibility survey.
 *
 * <p>Run with: {@code mvn -Pperf -pl agent-model-gateway clean test}
 * Results: {@code agent-model-gateway/target/jmh-results/*.json}
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Benchmark)
public class TokenCounterPerfTest {

    /** Text length in characters. */
    @Param({"100", "1000", "10000"})
    public int textLength;

    /** Fraction of characters that are CJK (0.0 = pure ASCII, 1.0 = pure CJK). */
    @Param({"0.0", "0.5", "1.0"})
    public double cjkRatio;

    private TokenCounterImpl counter;
    private String text;

    @Setup
    public void setup() {
        counter = new TokenCounterImpl();
        int cjkCount = (int) (textLength * cjkRatio);
        int asciiCount = textLength - cjkCount;
        StringBuilder sb = new StringBuilder(textLength + 1);
        // Interleave CJK and ASCII to avoid branch-predictor bias from contiguous blocks.
        // CJK block U+4E00..U+4E63 (100 chars) — avoids surrogates, stays in BMP.
        for (int i = 0; i < textLength; i++) {
            if (i < cjkCount) {
                sb.append((char) (0x4E00 + (i % 100)));
            } else {
                sb.append('a' + (i % 26));
            }
        }
        text = sb.toString();
    }

    @Benchmark
    public void count(Blackhole bh) {
        bh.consume(counter.count(text));
    }
}
