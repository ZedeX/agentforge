package com.agent.memory.perf;

import com.agent.memory.api.impl.MemoryDeduperImpl;
import com.agent.memory.api.impl.MemoryExtractorImpl;
import com.agent.memory.config.MemoryProperties;
import com.agent.memory.extractor.MemoryExtractRule;
import com.agent.memory.model.MemoryRecord;
import com.agent.memory.model.TaskResult;
import com.agent.memory.enums.MemoryType;
import com.agent.memory.enums.TaskOutcome;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH microbenchmark for agent-memory hot path.
 *
 * <p>Covers two pure-CPU kernels (docs/tests/test-plan.md: Vector recall Top-10 &lt; 50ms;
 * memory extraction + dedup are the CPU-bound sub-components):
 * <ul>
 *   <li>{@link MemoryExtractorImpl#extractFromTaskResult(TaskResult)} — dispatch by TaskOutcome + content filter</li>
 *   <li>{@link MemoryDeduperImpl#dedup(List)} — group by contentHash + sort by createdAt + keep earliest</li>
 * </ul>
 *
 * <p>Run with: {@code mvn -Pperf -pl agent-memory clean test}
 * Results: {@code agent-memory/target/jmh-results/*.json}
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Benchmark)
public class MemoryRecallPerfTest {

    /** Number of TaskResult entries to extract from (extractFromTaskResult loop). */
    @Param({"1", "50"})
    public int taskResultCount;

    /** Number of MemoryRecord entries in the dedup batch. */
    @Param({"100", "1000"})
    public int dedupBatchSize;

    /** Duplicate ratio in the dedup batch (0.0 = all unique, 0.5 = 50% duplicates). */
    @Param({"0.0", "0.5"})
    public double duplicateRatio;

    private MemoryExtractorImpl extractor;
    private List<TaskResult> taskResults;

    private MemoryDeduperImpl deduper;
    private List<MemoryRecord> dedupBatch;

    @Setup
    public void setup() {
        extractor = new MemoryExtractorImpl(new MemoryExtractRule(20, "spam,test,placeholder"));
        taskResults = new ArrayList<>(taskResultCount);
        for (int i = 0; i < taskResultCount; i++) {
            TaskResult tr = new TaskResult("task-" + i, TaskOutcome.SUCCESS,
                    "Investigate " + (i % 10) + " anomalies in the production cluster and summarize root causes.");
            List<String> steps = new ArrayList<>(5);
            for (int j = 0; j < 5; j++) {
                steps.add("Step " + j + ": collected metrics from node-" + j + " and verified alert signatures.");
            }
            tr.setSteps(steps);
            taskResults.add(tr);
        }

        // MemoryDeduperImpl.dedup() never touches the repository, so null is safe.
        deduper = new MemoryDeduperImpl(null, new MemoryProperties());
        dedupBatch = new ArrayList<>(dedupBatchSize);
        int uniqueCount = (int) (dedupBatchSize * (1.0 - duplicateRatio));
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < dedupBatchSize; i++) {
            String hash = (i < uniqueCount) ? sha256Stub(i) : sha256Stub(i % uniqueCount);
            MemoryRecord r = new MemoryRecord("mem-" + i, MemoryType.SEMANTIC, "content-" + i);
            r.setContentHash(hash);
            r.setCreatedAt(base.plusSeconds(i));
            dedupBatch.add(r);
        }
    }

    @Benchmark
    public void extractFromTaskResult(Blackhole bh) {
        for (TaskResult tr : taskResults) {
            bh.consume(extractor.extractFromTaskResult(tr));
        }
    }

    @Benchmark
    public void dedup(Blackhole bh) {
        bh.consume(deduper.dedup(dedupBatch));
    }

    /** Stable pseudo-hash for dedup grouping (not real SHA-256 — just for contentHash field). */
    private static String sha256Stub(int seed) {
        return Integer.toHexString(0x7fffffff & (seed * 0x9E3779B1));
    }
}
