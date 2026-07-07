package com.agent.riskcontrol.api;

import com.agent.riskcontrol.model.CheckPermissionRequest;
import com.agent.riskcontrol.model.CheckPermissionResponse;

/**
 * Permission checking port.
 *
 * <p>Checks whether a user has permission to perform an action on a resource.
 */
public interface PermissionChecker {

    /**
     * Check permission for an operation.
     *
     * @param request permission check request
     * @return permission check response with allowed/denied status
     */
    CheckPermissionResponse check(CheckPermissionRequest request);
}
