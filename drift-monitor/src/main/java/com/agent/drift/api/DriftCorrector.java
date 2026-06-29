package com.agent.drift.api;

import com.agent.drift.model.DriftCorrectAction;

/**
 * Drift corrector port (F11 L3: session-level inject / system-level rollback).
 */
public interface DriftCorrector {

    /**
     * Apply a drift correction action.
     *
     * @return true when correction applied successfully.
     */
    boolean correct(DriftCorrectAction action);
}
