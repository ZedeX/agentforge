package com.agent.runtime.exception;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;

/**
 * Thrown when a Resilience4j circuit breaker is OPEN and rejects a call (T9, doc 06 §6).
 *
 * <p>Also used for legacy ReAct loop count exceeding max limit (doc 11 F6 熔断).</p>
 */
public class CircuitOpenException extends BusinessException {

    /** Circuit breaker name that triggered the open state */
    private final String circuitName;
    /** Current state of the circuit breaker (OPEN / HALF_OPEN) */
    private final String circuitState;
    /** Legacy field: loop count when exceeded max limit */
    private final int loopCount;

    /**
     * T9 constructor: Resilience4j circuit breaker open.
     */
    public CircuitOpenException(String circuitName, String circuitState, Throwable cause) {
        super(ErrorCode.CIRCUIT_OPEN,
                "CIRCUIT_OPEN: circuit=" + circuitName + " state=" + circuitState + ", call rejected");
        this.circuitName = circuitName;
        this.circuitState = circuitState;
        this.loopCount = 0;
    }

    /**
     * Legacy constructor: ReAct loop count exceeded max (doc 11 F6).
     */
    public CircuitOpenException(int loopCount) {
        super(ErrorCode.CIRCUIT_OPEN,
                "CIRCUIT_OPEN: loop_count=" + loopCount + " > max=10, subtask failed");
        this.circuitName = "react-loop";
        this.circuitState = "OPEN";
        this.loopCount = loopCount;
    }

    public String getCircuitName() { return circuitName; }
    public String getCircuitState() { return circuitState; }
    public int getLoopCount() { return loopCount; }
}
