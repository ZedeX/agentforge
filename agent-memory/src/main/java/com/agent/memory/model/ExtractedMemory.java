package com.agent.memory.model;

import com.agent.memory.enums.MemoryType;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracted memory unit (F12.D2 extraction branch).
 *
 * <p>Holds episodic / semantic / procedural extracted payload.</p>
 */
public class ExtractedMemory implements Serializable {

    private static final long serialVersionUID = 1L;

    private MemoryType type;
    private List<String> stepSequence = new ArrayList<>();
    private Instant extractedAt;
    private String fact;
    private String source;
    private String patternTemplate;

    public ExtractedMemory() {
    }

    public ExtractedMemory(MemoryType type) {
        this.type = type;
        this.extractedAt = Instant.now();
    }

    public MemoryType getType() {
        return type;
    }

    public void setType(MemoryType type) {
        this.type = type;
    }

    public List<String> getStepSequence() {
        return stepSequence;
    }

    public void setStepSequence(List<String> stepSequence) {
        this.stepSequence = stepSequence;
    }

    public Instant getExtractedAt() {
        return extractedAt;
    }

    public void setExtractedAt(Instant extractedAt) {
        this.extractedAt = extractedAt;
    }

    public String getFact() {
        return fact;
    }

    public void setFact(String fact) {
        this.fact = fact;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getPatternTemplate() {
        return patternTemplate;
    }

    public void setPatternTemplate(String patternTemplate) {
        this.patternTemplate = patternTemplate;
    }
}
