package com.agent.hallucination.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * A factual claim produced by an agent step (F10 L2 self-check input).
 */
public class Claim implements Serializable {

    private static final long serialVersionUID = 1L;

    private String claimId;
    private String text;
    private boolean hasSourceTag;
    private String sourceRef;
    private String scene;

    public Claim() {
    }

    public Claim(String text, boolean hasSourceTag) {
        this.text = text;
        this.hasSourceTag = hasSourceTag;
    }

    public String getClaimId() {
        return claimId;
    }

    public void setClaimId(String claimId) {
        this.claimId = claimId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isHasSourceTag() {
        return hasSourceTag;
    }

    public void setHasSourceTag(boolean hasSourceTag) {
        this.hasSourceTag = hasSourceTag;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Claim)) return false;
        Claim claim = (Claim) o;
        return Objects.equals(claimId, claim.claimId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(claimId);
    }
}
