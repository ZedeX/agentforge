package com.agent.riskcontrol.model;

/**
 * POJO for a single content violation.
 */
public class ContentViolation {

    private String category;
    private String severity;
    private String detail;
    private int position;

    public ContentViolation() {
    }

    public ContentViolation(String category, String severity, String detail, int position) {
        this.category = category;
        this.severity = severity;
        this.detail = detail;
        this.position = position;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
