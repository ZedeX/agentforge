package com.agent.hallucination.api;

import com.agent.hallucination.model.HallucinationMetric;

/**
 * Layer 6 hallucination metric writer port (F10 L6: agent_metrics_daily).
 */
public interface HallucinationMetricWriter {

    /**
     * Write a hallucination rate metric row.
     */
    void write(HallucinationMetric metric);
}
