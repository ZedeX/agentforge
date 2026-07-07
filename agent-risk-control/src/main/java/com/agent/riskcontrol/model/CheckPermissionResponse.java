package com.agent.riskcontrol.model;

import java.util.ArrayList;
import java.util.List;

/**
 * POJO for permission check response.
 */
public class CheckPermissionResponse {

    private boolean allowed;
    private String reason;
    private List<String> requiredRoles = new ArrayList<>();

    public CheckPermissionResponse() {
    }

    public CheckPermissionResponse(boolean allowed, String reason, List<String> requiredRoles) {
        this.allowed = allowed;
        this.reason = reason;
        this.requiredRoles = requiredRoles != null ? requiredRoles : new ArrayList<>();
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<String> getRequiredRoles() {
        return requiredRoles;
    }

    public void setRequiredRoles(List<String> requiredRoles) {
        this.requiredRoles = requiredRoles;
    }
}
