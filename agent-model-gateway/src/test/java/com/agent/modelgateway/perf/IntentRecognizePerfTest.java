package com.agent.modelgateway.perf;

import com.agent.modelgateway.api.impl.ModelRouterImpl;
import com.agent.modelgateway.enums.Scene;
import com.agent.modelgateway.model.ModelRouteRule;
import com.agent.modelgateway.model.RouteResult;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH microbenchmark for {@link ModelRouterImpl#route(Scene, String)}.
 *
 * <p>Target (docs/tests/test-plan.md): Intent recognition P95 ≤ 200ms. Model routing is the
 * dispatch kernel that runs after intent classification. The router does an O(rules) linear scan
 * over a {@link java.util.concurrent.CopyOnWriteArrayList} of rules, falling back to GENERIC.
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
public class IntentRecognizePerfTest {

    /** Number of routing rules seeded (default 3 + N extra GENERIC). */
    @Param({"3", "30", "300"})
    public int ruleCount;

    /** Scene to route (INTENT / AUDIT / GENERIC). */
    @Param({"INTENT", "GENERIC"})
    public String sceneName;

    /** Whether preferredModel short-circuits the linear scan. */
    @Param({"false", "true"})
    public boolean hasPreferredModel;

    private ModelRouterImpl router;
    private Scene scene;
    private String preferredModel;

    @Setup
    public void setup() {
        router = new ModelRouterImpl();
        // Seed extra rules to grow the linear-scan list (CopyOnWriteArrayList — add only in setup).
        for (int i = 0; i < ruleCount - 3; i++) {
            ModelRouteRule rule = new ModelRouteRule(Scene.GENERIC, 100 + i, "provider-" + i, "fallback-" + i);
            rule.setEnabled(true);
            router.addRule(rule);
        }
        scene = Scene.fromCode(sceneName);
        preferredModel = hasPreferredModel ? "qwen-turbo" : null;
    }

    @Benchmark
    public void route(Blackhole bh) {
        RouteResult result = router.route(scene, preferredModel);
        bh.consume(result);
    }
}
