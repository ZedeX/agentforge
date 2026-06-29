package com.agent.memory.model;

import java.io.Serializable;

/**
 * Memory topic for distillation (F12.D7: same-topic fragments >= 5).
 */
public class MemoryTopic implements Serializable {

    private static final long serialVersionUID = 1L;

    private String topic;
    private int fragmentCount;
    private String summary;
    private double compressionRatio;
    private boolean distilled;

    public MemoryTopic() {
    }

    public MemoryTopic(String topic, int fragmentCount) {
        this.topic = topic;
        this.fragmentCount = fragmentCount;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getFragmentCount() {
        return fragmentCount;
    }

    public void setFragmentCount(int fragmentCount) {
        this.fragmentCount = fragmentCount;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public double getCompressionRatio() {
        return compressionRatio;
    }

    public void setCompressionRatio(double compressionRatio) {
        this.compressionRatio = compressionRatio;
    }

    public boolean isDistilled() {
        return distilled;
    }

    public void setDistilled(boolean distilled) {
        this.distilled = distilled;
    }
}
