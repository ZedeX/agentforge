package com.agent.riskcontrol.model;

/**
 * POJO for permission check request.
 */
public class CheckPermissionRequest {

    private String userId;
    private String resource;
    private String action;
    private String resourceType;

    public CheckPermissionRequest() {
    }

    public CheckPermissionRequest(String userId, String resource, String action, String resourceType) {
        this.userId = userId;
        this.resource = resource;
        this.action = action;
        this.resourceType = resourceType;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }
}
