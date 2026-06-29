package com.agent.runtime.model;

import com.agent.runtime.enums.ReActPhaseType;

/**
 * Step state snapshot for checkpoint persistence (doc 11-detail-flow F6, UT-RT-008/010).
 */
public class StepState {

    private String agentId;
    private int stepNo;
    private ReActPhaseType phase;
    private String checkpointData;

    public StepState() {
    }

    public StepState(String agentId, int stepNo, ReActPhaseType phase) {
        this.agentId = agentId;
        this.stepNo = stepNo;
        this.phase = phase;
    }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public int getStepNo() { return stepNo; }
    public void setStepNo(int stepNo) { this.stepNo = stepNo; }

    public ReActPhaseType getPhase() { return phase; }
    public void setPhase(ReActPhaseType phase) { this.phase = phase; }

    public String getCheckpointData() { return checkpointData; }
    public void setCheckpointData(String checkpointData) { this.checkpointData = checkpointData; }
}
