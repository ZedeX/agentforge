package com.agent.riskcontrol.api.impl;

import com.agent.riskcontrol.config.RiskControlProperties;
import com.agent.riskcontrol.model.CheckPermissionRequest;
import com.agent.riskcontrol.model.CheckPermissionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD tests for R-06: PermissionChecker must use JWT-extracted role from request,
 * NOT hardcoded user-role mappings.
 */
class PermissionCheckerImplTest {

    private PermissionCheckerImpl checker;
    private RiskControlProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RiskControlProperties();
        // Default action = deny (production-safe)
        properties.getPermission().setDefaultAction("deny");
        checker = new PermissionCheckerImpl(properties);
    }

    // ===== R-06: Role from request, not hardcoded lookup =====

    @Nested
    @DisplayName("R-06: Role from JWT claims (passed via request)")
    class RoleFromRequest {

        @Test
        @DisplayName("should_AllowRead_When_RequestRoleIsUser")
        void should_AllowRead_When_RequestRoleIsUser() {
            CheckPermissionRequest req = new CheckPermissionRequest("any-user-id", "tool-1", "read", "tool");
            req.setRole("user");

            CheckPermissionResponse resp = checker.check(req);

            assertThat(resp.isAllowed()).isTrue();
            assertThat(resp.getReason()).contains("user");
        }

        @Test
        @DisplayName("should_DenyDelete_When_RequestRoleIsUser")
        void should_DenyDelete_When_RequestRoleIsUser() {
            CheckPermissionRequest req = new CheckPermissionRequest("any-user-id", "tool-1", "delete", "tool");
            req.setRole("user");

            CheckPermissionResponse resp = checker.check(req);

            assertThat(resp.isAllowed()).isFalse();
        }

        @Test
        @DisplayName("should_AllowAllActions_When_RequestRoleIsAdmin")
        void should_AllowAllActions_When_RequestRoleIsAdmin() {
            for (String action : new String[]{"create", "read", "update", "delete", "execute"}) {
                CheckPermissionRequest req = new CheckPermissionRequest("any-user-id", "res", action, "tool");
                req.setRole("admin");

                CheckPermissionResponse resp = checker.check(req);

                assertThat(resp.isAllowed())
                        .as("admin should be allowed to " + action)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("should_AllowReadOnly_When_RequestRoleIsViewer")
        void should_AllowReadOnly_When_RequestRoleIsViewer() {
            CheckPermissionRequest readReq = new CheckPermissionRequest("any-user-id", "res", "read", "tool");
            readReq.setRole("viewer");
            assertThat(checker.check(readReq).isAllowed()).isTrue();

            CheckPermissionRequest createReq = new CheckPermissionRequest("any-user-id", "res", "create", "tool");
            createReq.setRole("viewer");
            assertThat(checker.check(createReq).isAllowed()).isFalse();
        }

        @Test
        @DisplayName("should_Deny_When_NoRoleProvided_AndDefaultIsDeny")
        void should_Deny_When_NoRoleProvided_AndDefaultIsDeny() {
            CheckPermissionRequest req = new CheckPermissionRequest("some-user", "res", "read", "tool");
            // no role set

            CheckPermissionResponse resp = checker.check(req);

            assertThat(resp.isAllowed()).isFalse();
            assertThat(resp.getReason()).contains("deny").containsIgnoringCase("no role");
        }

        @Test
        @DisplayName("should_Allow_When_NoRoleProvided_AndDefaultIsAllow")
        void should_Allow_When_NoRoleProvided_AndDefaultIsAllow() {
            properties.getPermission().setDefaultAction("allow");
            checker = new PermissionCheckerImpl(properties);

            CheckPermissionRequest req = new CheckPermissionRequest("some-user", "res", "read", "tool");

            CheckPermissionResponse resp = checker.check(req);

            assertThat(resp.isAllowed()).isTrue();
        }
    }

    // ===== No hardcoded user-role mappings =====

    @Nested
    @DisplayName("R-06: Hardcoded user-role mappings removed")
    class HardcodedUserRolesRemoved {

        @Test
        @DisplayName("should_Deny_When_UserIdIsAdmin001_ButNoRoleProvided")
        void should_Deny_When_UserIdIsAdmin001_ButNoRoleProvided() {
            // Previously admin-001 was hardcoded as admin. Now without role, should be denied.
            CheckPermissionRequest req = new CheckPermissionRequest("admin-001", "res", "read", "tool");
            // no role → default deny

            CheckPermissionResponse resp = checker.check(req);

            assertThat(resp.isAllowed()).isFalse();
        }

        @Test
        @DisplayName("should_Allow_When_UserIdIsAdmin001_AndRoleAdminProvided")
        void should_Allow_When_UserIdIsAdmin001_AndRoleAdminProvided() {
            CheckPermissionRequest req = new CheckPermissionRequest("admin-001", "res", "delete", "tool");
            req.setRole("admin");

            CheckPermissionResponse resp = checker.check(req);

            assertThat(resp.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should_Deny_When_UnknownRoleProvided")
        void should_Deny_When_UnknownRoleProvided() {
            CheckPermissionRequest req = new CheckPermissionRequest("user-x", "res", "read", "tool");
            req.setRole("superadmin");  // not in ROLE_PERMISSIONS

            CheckPermissionResponse resp = checker.check(req);

            assertThat(resp.isAllowed()).isFalse();
        }
    }

    // ===== Input validation =====

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("should_Deny_When_UserIdIsNull")
        void should_Deny_When_UserIdIsNull() {
            CheckPermissionRequest req = new CheckPermissionRequest(null, "res", "read", "tool");

            CheckPermissionResponse resp = checker.check(req);

            assertThat(resp.isAllowed()).isFalse();
            assertThat(resp.getReason()).containsIgnoringCase("user id");
        }

        @Test
        @DisplayName("should_Deny_When_ActionIsNull")
        void should_Deny_When_ActionIsNull() {
            CheckPermissionRequest req = new CheckPermissionRequest("user-1", "res", null, "tool");

            CheckPermissionResponse resp = checker.check(req);

            assertThat(resp.isAllowed()).isFalse();
            assertThat(resp.getReason()).containsIgnoringCase("action");
        }
    }
}
