package com.agent.runtime.exception;

/**
 * Thrown when ReAct loop count exceeds max limit (10).
 * Ref: doc 11-detail-flow F6 熔断, UT-RT-007.
 */
public class CircuitOpenException extends RuntimeException {

    private final int loopCount;

    public CircuitOpenException(int loopCount) {
        super("CIRCUIT_OPEN: loop_count=" + loopCount + " > max=10, subtask failed");
        this.loopCount = loopCount;
    }

    public int getLoopCount() {
        return loopCount;
    }
}
