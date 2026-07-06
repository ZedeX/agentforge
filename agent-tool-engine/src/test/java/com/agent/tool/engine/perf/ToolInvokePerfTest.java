package com.agent.tool.engine.perf;

import com.agent.tool.engine.api.impl.ResultCleanerImpl;
import com.agent.tool.engine.cache.ParamsHasher;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH microbenchmark for tool-engine hot path.
 *
 * <p>Covers two pure-CPU kernels (docs/tests/test-plan.md: Tool invoke P95 ≤ 800ms):
 * <ul>
 *   <li>{@link ResultCleanerImpl#clean(String)} — ANSI strip → PII redact → byte-truncate → trim pipeline</li>
 *   <li>{@link ParamsHasher#hash(Map)} — TreeMap canonicalize → Jackson serialize → SHA-256 → hex</li>
 * </ul>
 *
 * <p>Run with: {@code mvn -Pperf -pl agent-tool-engine clean test}
 * Results: {@code agent-tool-engine/target/jmh-results/*.json}
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Benchmark)
public class ToolInvokePerfTest {

    /** Raw tool output length in characters. */
    @Param({"100", "2000", "20000"})
    public int outputLength;

    /** PII density (0.0 = no PII, 1.0 = every line has PII). */
    @Param({"0.0", "0.3"})
    public double piiDensity;

    private ResultCleanerImpl cleaner;
    private String rawOutput;

    /** Param map sizes for ParamsHasher (kept small to reflect real tool-call param shapes). */
    @Param({"5", "20"})
    public int paramMapSize;

    private Map<String, Object> params;

    @Setup
    public void setup() {
        cleaner = new ResultCleanerImpl();
        rawOutput = buildRawOutput(outputLength, piiDensity);
        params = buildParams(paramMapSize);
    }

    @Benchmark
    public void clean(Blackhole bh) {
        bh.consume(cleaner.clean(rawOutput));
    }

    @Benchmark
    public void hashParams(Blackhole bh) {
        bh.consume(ParamsHasher.hash(params));
    }

    private static String buildRawOutput(int length, double piiDensity) {
        StringBuilder sb = new StringBuilder(length + 128);
        String[] piiSamples = {
                "Contact: 13800138000",                        // PHONE
                "Email: user@example.com",                     // EMAIL
                "API key: sk-abcdef1234567890abcdef1234567890", // API_KEY
                "ID: 110101199001011234"                       // ID_CARD
        };
        int line = 0;
        while (sb.length() < length) {
            if (piiDensity > 0 && line % (int) Math.max(1, 1.0 / piiDensity) == 0) {
                sb.append(piiSamples[line % piiSamples.length]);
            } else {
                sb.append("\u001B[32mOK\u001B[0m line ").append(line)
                  .append(": normal tool output without sensitive data.");
            }
            sb.append('\n');
            line++;
        }
        return sb.substring(0, Math.min(sb.length(), length));
    }

    private static Map<String, Object> buildParams(int size) {
        Map<String, Object> m = new LinkedHashMap<>(size * 2);
        for (int i = 0; i < size; i++) {
            m.put("param_" + i, "value_" + i);
        }
        return m;
    }
}
