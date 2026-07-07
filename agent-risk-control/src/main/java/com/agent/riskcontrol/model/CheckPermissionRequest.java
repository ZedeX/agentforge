package com.agent.riskcontrol.model;

/**
 * POJO for permission check request.
 */
public class CheckPermissionRequest {

    private String userId;
    private String resource;
    private String action;
    private String resourceType;
    /** R-06: JWT-extracted role from gateway (admin|user|viewer). Null = no role provided. */
    private String role;

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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
