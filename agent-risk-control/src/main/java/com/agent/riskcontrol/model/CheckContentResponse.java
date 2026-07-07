package com.agent.riskcontrol.model;

import java.util.ArrayList;
import java.util.List;

/**
 * POJO for content safety check response.
 */
public class CheckContentResponse {

    private boolean safe;
    private List<ContentViolation> violations = new ArrayList<>();
    private String sanitizedContent;

    public CheckContentResponse() {
    }

    public CheckContentResponse(boolean safe, List<ContentViolation> violations, String sanitizedContent) {
        this.safe = safe;
        this.violations = violations != null ? violations : new ArrayList<>();
        this.sanitizedContent = sanitizedContent;
    }

    public boolean isSafe() {
        return safe;
    }

    public void setSafe(boolean safe) {
        this.safe = safe;
    }

    public List<ContentViolation> getViolations() {
        return violations;
    }

    public void setViolations(List<ContentViolation> violations) {
        this.violations = violations;
    }

    public String getSanitizedContent() {
        return sanitizedContent;
    }

    public void setSanitizedContent(String sanitizedContent) {
        this.sanitizedContent = sanitizedContent;
    }
}
