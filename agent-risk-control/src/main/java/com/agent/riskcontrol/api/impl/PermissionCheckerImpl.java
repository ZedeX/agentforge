package com.agent.riskcontrol.api.impl;

import com.agent.riskcontrol.api.PermissionChecker;
import com.agent.riskcontrol.config.RiskControlProperties;
import com.agent.riskcontrol.model.CheckPermissionRequest;
import com.agent.riskcontrol.model.CheckPermissionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simple role-based permission checker implementation.
 *
 * <p>Role hierarchy:
 * <ul>
 *   <li>admin: all actions (create, read, update, delete, execute)</li>
 *   <li>user: read, create</li>
 *   <li>viewer: read only</li>
 * </ul>
 *
 * <p>When no role is found for the user, the default action from configuration applies.
 */
@Slf4j
@Component
public class PermissionCheckerImpl implements PermissionChecker {

    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
            "admin", Set.of("create", "read", "update", "delete", "execute"),
            "user", Set.of("read", "create"),
            "viewer", Set.of("read")
    );

    /** Simulated user-role mapping. In production this would query an IAM service. */
    private static final Map<String, String> USER_ROLES = Map.of(
            "admin-001", "admin",
            "user-001", "user",
            "viewer-001", "viewer"
    );

    private final RiskControlProperties properties;

    public PermissionCheckerImpl(RiskControlProperties properties) {
        this.properties = properties;
    }

    @Override
    public CheckPermissionResponse check(CheckPermissionRequest request) {
        String userId = request.getUserId();
        String action = request.getAction();

        if (userId == null || userId.isEmpty()) {
            return new CheckPermissionResponse(false, "User ID is required", List.of());
        }

        if (action == null || action.isEmpty()) {
            return new CheckPermissionResponse(false, "Action is required", List.of());
        }

        String role = USER_ROLES.get(userId);

        if (role == null) {
            // No role found, apply default action
            boolean allowed = "allow".equalsIgnoreCase(properties.getPermission().getDefaultAction());
            String reason = allowed ? "Default action: allow" : "Default action: deny - no role found for user";
            log.info("Permission check: userId={} action={} noRole defaultAction={}",
                    userId, action, properties.getPermission().getDefaultAction());
            return new CheckPermissionResponse(allowed, reason, List.of());
        }

        Set<String> permissions = ROLE_PERMISSIONS.get(role);
        boolean allowed = permissions != null && permissions.contains(action);

        String reason = allowed
                ? "Role " + role + " permits action " + action
                : "Role " + role + " does not permit action " + action;

        // Find roles that would grant access
        List<String> requiredRoles = ROLE_PERMISSIONS.entrySet().stream()
                .filter(e -> e.getValue().contains(action))
                .map(Map.Entry::getKey)
                .toList();

        log.info("Permission check: userId={} role={} action={} allowed={}",
                userId, role, action, allowed);

        return new CheckPermissionResponse(allowed, reason, requiredRoles);
    }
}
