package com.agent.runtime.perf;

import com.agent.runtime.enums.ReflexionResult;
import com.agent.runtime.loop.ReflexionPromptBuilder;
import com.agent.runtime.model.ReActContext;
import com.agent.runtime.model.StepState;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.agent.runtime.enums.ReActPhaseType.THINK;

/**
 * JMH microbenchmark for {@link ReflexionPromptBuilder}.
 *
 * <p>Covers two pure-CPU methods (docs/tests/test-plan.md: Token compress &lt; 200ms/500ms/1s):
 * <ul>
 *   <li>{@link ReflexionPromptBuilder#build(ReActContext, List, String)} — StringBuilder assembly + truncation + max-5 history</li>
 *   <li>{@link ReflexionPromptBuilder#parseDecision(String)} — toUpperCase + contains</li>
 * </ul>
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
public class ReflexionBuildPerfTest {

    /** Number of historical StepState entries (ReflexionPromptBuilder truncates to max 5). */
    @Param({"1", "5", "20"})
    public int historySize;

    private ReflexionPromptBuilder builder;
    private ReActContext ctx;
    private List<StepState> history;
    private String modelOutput;

    @Setup
    public void setup() {
        builder = new ReflexionPromptBuilder();
        ctx = ReActContext.forTest("task-perf", 20, 32000);
        ctx.setStepNumber(historySize + 1);
        ctx.setRetryCount(1);
        ctx.setMaxRetry(3);
        ctx.setTokenUsed(historySize * 200);
        ctx.setTokenBudget(32000);
        ctx.setUserInput("Explain the difference between ZGC and G1 GC in JDK 17, with concrete latency tradeoffs.");

        history = new ArrayList<>(historySize);
        for (int i = 0; i < historySize; i++) {
            StepState s = new StepState("agent-perf", i + 1, THINK);
            s.setThinkContent("Step " + i + ": analyzed topic branch covering memory-region design and pause-time budgets.");
            s.setObserveContent("Observed intermediate result: pause time scales with heap set, not with live-set.");
            s.setErrorMessage(null);
            history.add(s);
        }
        modelOutput = "CONTINUE\nReasoning: the next step should verify with a benchmark on JDK 17 with -XX:+UseZGC.";
    }

    @Benchmark
    public void build(Blackhole bh) {
        bh.consume(builder.build(ctx, history, "low_confidence"));
    }

    @Benchmark
    public void parseDecision(Blackhole bh) {
        ReflexionResult result = builder.parseDecision(modelOutput);
        bh.consume(result);
    }
}
