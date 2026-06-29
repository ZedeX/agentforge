package com.agent.drift.api;

import com.agent.drift.model.BehaviorBaseline;

/**
 * Baseline anchor port (F11 L1: anchor behavior baseline on first run).
 *
 * <p>Writes eval_baseline row on first run; subsequent runs compare against baseline.</p>
 */
public interface BaselineAnchor {

    /**
     * Anchor a behavior baseline.
     *
     * @return true when anchor persisted; false when baseline already exists.
     */
    boolean anchor(BehaviorBaseline baseline);
}
