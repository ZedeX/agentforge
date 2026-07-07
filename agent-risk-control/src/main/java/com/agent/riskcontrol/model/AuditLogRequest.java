package com.agent.riskcontrol.model;

/**
 * POJO for audit log request.
 */
public class AuditLogRequest {

    private String action;
    private String actorId;
    private String resourceType;
    private String resourceId;
    private String result;
    private String detail;

    public AuditLogRequest() {
    }

    public AuditLogRequest(String action, String actorId, String resourceType,
                           String resourceId, String result, String detail) {
        this.action = action;
        this.actorId = actorId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.result = result;
        this.detail = detail;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
