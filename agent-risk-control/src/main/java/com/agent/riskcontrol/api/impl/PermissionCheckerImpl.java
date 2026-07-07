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
 * Role-based permission checker implementation.
 *
 * <p>R-06: Role is extracted from JWT claims by the gateway and passed via
 * {@link CheckPermissionRequest#getRole()}. No hardcoded user-role mappings.
 *
 * <p>Role hierarchy:
 * <ul>
 *   <li>admin: all actions (create, read, update, delete, execute)</li>
 *   <li>user: read, create</li>
 *   <li>viewer: read only</li>
 * </ul>
 *
 * <p>When no role is provided in the request, the default action from configuration applies.
 */
@Slf4j
@Component
public class PermissionCheckerImpl implements PermissionChecker {

    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
            "admin", Set.of("create", "read", "update", "delete", "execute"),
            "user", Set.of("read", "create"),
            "viewer", Set.of("read")
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

        // R-06: Role from JWT claims (passed by gateway via gRPC), not hardcoded lookup
        String role = request.getRole();

        if (role == null || role.isEmpty()) {
            // No role provided, apply default action
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
                : (permissions == null)
                    ? "Unknown role " + role + " has no permissions"
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
