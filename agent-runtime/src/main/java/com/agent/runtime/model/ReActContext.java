package com.agent.runtime.model;

import com.agent.runtime.enums.ReActPhaseType;

import java.util.HashMap;
import java.util.Map;

/**
 * ReAct loop execution context (doc 06 §2, F6 think/act/observe/finish).
 */
public class ReActContext {

    private String agentId;
    private String taskId;
    private ReActPhaseType phase;
    private int loopCount;
    private String finalAnswer;
    private Map<String, Object> memory;

    public ReActContext() {
        this.memory = new HashMap<>();
        this.phase = ReActPhaseType.THINK;
        this.loopCount = 0;
    }

    public ReActContext(String agentId, String taskId) {
        this();
        this.agentId = agentId;
        this.taskId = taskId;
    }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public ReActPhaseType getPhase() { return phase; }
    public void setPhase(ReActPhaseType phase) { this.phase = phase; }

    public int getLoopCount() { return loopCount; }
    public void setLoopCount(int loopCount) { this.loopCount = loopCount; }
    public void incrementLoopCount() { this.loopCount++; }

    public String getFinalAnswer() { return finalAnswer; }
    public void setFinalAnswer(String finalAnswer) { this.finalAnswer = finalAnswer; }

    public Map<String, Object> getMemory() { return memory; }
    public void setMemory(Map<String, Object> memory) { this.memory = memory; }
}
