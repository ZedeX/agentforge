package com.agent.drift.api;

import com.agent.drift.enums.DriftType;
import com.agent.drift.model.DriftSignal;

/**
 * Drift detector port (F11 L2: behavior / effect / alignment / memory drift detection).
 */
public interface DriftDetector {

    /**
     * Detect drift type from a drift signal.
     */
    DriftType detect(DriftSignal signal);
}
