package com.agent.modelgateway.perf;

import com.agent.modelgateway.api.impl.PromptCacheImpl;
import com.agent.modelgateway.model.ChatReply;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH microbenchmark for {@link PromptCacheImpl}.
 *
 * <p>Covers the MD5 + {@link java.util.concurrent.ConcurrentHashMap} prompt-cache hot path
 * (docs/tests/test-plan.md: prompt cache lookup is a P95-critical sub-component).
 * Flags the per-byte {@code String.format("%02x", b)} allocation hotspot in the MD5 hex encoder
 * noted during the JMH feasibility survey.
 *
 * <p>Run with: {@code mvn -Pperf -pl agent-model-gateway clean test}
 * Results: {@code agent-model-gateway/target/jmh-results/*.json}
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Benchmark)
public class PromptCachePerfTest {

    /** Prompt length in characters (cache key = MD5 of first 256 chars). */
    @Param({"64", "256", "1024"})
    public int promptLength;

    /** Cache pre-population size (concurrent map cardinality). */
    @Param({"0", "1000"})
    public int cacheSize;

    private PromptCacheImpl cache;
    private String tenantId;
    private String promptHit;
    private String promptMiss;
    private ChatReply reply;

    @Setup
    public void setup() {
        cache = new PromptCacheImpl();
        tenantId = "tenant-perf";
        reply = new ChatReply("qwen-turbo", "qwen-turbo", "cached-reply", 12, 8, 110L, true);

        // Pre-populate cache with N entries of the same length.
        for (int i = 0; i < cacheSize; i++) {
            String p = buildPrompt(promptLength, i);
            cache.put(tenantId, p, reply);
        }

        // Hit path: a prompt that was inserted (use last seed).
        promptHit = buildPrompt(promptLength, Math.max(0, cacheSize - 1));
        // Miss path: a prompt that was never inserted (unique salt).
        promptMiss = buildPrompt(promptLength, -1);
    }

    @Benchmark
    public void lookupHit(Blackhole bh) {
        bh.consume(cache.lookup(tenantId, promptHit));
    }

    @Benchmark
    public void lookupMiss(Blackhole bh) {
        bh.consume(cache.lookup(tenantId, promptMiss));
    }

    @Benchmark
    public void put(Blackhole bh) {
        cache.put(tenantId, promptMiss, reply);
        bh.consume("void");
    }

    private static String buildPrompt(int length, int salt) {
        StringBuilder sb = new StringBuilder(length + 8);
        sb.append("salt").append(salt).append(':');
        while (sb.length() < length) {
            sb.append("Lorem ipsum dolor sit amet, consectetur adipiscing elit. ");
        }
        return sb.substring(0, length);
    }
}
