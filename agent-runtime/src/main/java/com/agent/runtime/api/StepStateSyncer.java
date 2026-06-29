package com.agent.runtime.api;

import com.agent.runtime.enums.ReActPhaseType;
import com.agent.runtime.model.StepState;

/**
 * Step state synchronizer for checkpoint persistence (doc 11-detail-flow F6, UT-RT-008/010).
 */
public interface StepStateSyncer {

    /**
     * Sync step state to Redis after each phase completed.
     *
     * @param agentId agent instance id
     * @param stepNo current step number
     * @param phase completed phase
     */
    void syncStepState(String agentId, int stepNo, ReActPhaseType phase);

    /**
     * Persist checkpoint for crash recovery.
     *
     * @param agentId agent instance id
     * @param stepNo current step number
     * @param checkpointData serialized context for recovery
     */
    void checkpoint(String agentId, int stepNo, String checkpointData);

    /**
     * Load latest checkpoint for recovery.
     *
     * @param agentId agent instance id
     * @return latest step state, or null if no checkpoint
     */
    StepState loadCheckpoint(String agentId);
}
