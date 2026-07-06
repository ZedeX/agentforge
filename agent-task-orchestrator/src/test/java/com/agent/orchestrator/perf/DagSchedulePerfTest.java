package com.agent.orchestrator.perf;

import com.agent.orchestrator.dag.DagGraph;
import com.agent.orchestrator.dag.DagValidator;
import com.agent.orchestrator.dag.TopologicalSorter;
import com.agent.orchestrator.dispatcher.BatchPartitioner;
import com.agent.orchestrator.model.DagEdge;
import com.agent.orchestrator.model.DagNode;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH microbenchmark for DAG scheduling hot path.
 *
 * <p>Covers three pure-CPU kernels (docs/tests/test-plan.md: DAG scheduling latency &lt; 100ms):
 * <ul>
 *   <li>{@link TopologicalSorter#sort(DagGraph)} — Kahn algorithm, O(V+E)</li>
 *   <li>{@link DagValidator#validate(List, List)} — 5 checks incl. cycle detection</li>
 *   <li>{@link BatchPartitioner#partition(DagGraph)} — level-by-level Kahn for batch dispatch</li>
 * </ul>
 *
 * <p>Run with: {@code mvn -Pperf -pl agent-task-orchestrator clean test}
 * Results: {@code agent-task-orchestrator/target/jmh-results/*.json}
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Benchmark)
public class DagSchedulePerfTest {

    /** Number of nodes in the synthetic DAG. */
    @Param({"10", "100", "1000"})
    public int nodeCount;

    /** DAG shape: "chain" (linear) or "wide" (single root, N-1 leaves). */
    @Param({"chain", "wide"})
    public String shape;

    private DagGraph graph;
    private List<DagNode> nodes;
    private List<DagEdge> edges;

    private final TopologicalSorter sorter = new TopologicalSorter();
    private final DagValidator validator = new DagValidator();
    private final BatchPartitioner partitioner = new BatchPartitioner();

    @Setup
    public void setup() {
        nodes = new ArrayList<>(nodeCount);
        edges = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            DagNode node = DagNode.builder()
                    .dagId(1L)
                    .nodeId("n" + i)
                    .nodeType("TASK")
                    .subtaskId("s" + i)
                    .title("node-" + i)
                    .status("pending")
                    .build();
            nodes.add(node);
        }
        if ("chain".equals(shape)) {
            // Linear chain: n0 -> n1 -> n2 -> ... -> n(N-1)
            for (int i = 0; i < nodeCount - 1; i++) {
                edges.add(DagEdge.builder()
                        .dagId(1L)
                        .parentNodeId("n" + i)
                        .childNodeId("n" + (i + 1))
                        .edgeType("DATA")
                        .build());
            }
        } else {
            // Wide: n0 -> n1, n0 -> n2, ..., n0 -> n(N-1)
            for (int i = 1; i < nodeCount; i++) {
                edges.add(DagEdge.builder()
                        .dagId(1L)
                        .parentNodeId("n0")
                        .childNodeId("n" + i)
                        .edgeType("DATA")
                        .build());
            }
        }
        graph = new DagGraph(nodes, edges);
    }

    @Benchmark
    public void topologicalSort(Blackhole bh) {
        bh.consume(sorter.sort(graph));
    }

    @Benchmark
    public void validate(Blackhole bh) {
        validator.validate(nodes, edges);
    }

    @Benchmark
    public void partition(Blackhole bh) {
        bh.consume(partitioner.partition(graph));
    }
}
