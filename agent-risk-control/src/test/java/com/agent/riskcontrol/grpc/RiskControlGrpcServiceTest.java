package com.agent.riskcontrol.grpc;

import agentplatform.riskcontrol.v1.AuditLogAck;
import agentplatform.riskcontrol.v1.AuditLogRequest;
import agentplatform.riskcontrol.v1.CheckContentRequest;
import agentplatform.riskcontrol.v1.CheckContentResponse;
import agentplatform.riskcontrol.v1.CheckPermissionRequest;
import agentplatform.riskcontrol.v1.CheckPermissionResponse;
import com.agent.riskcontrol.api.ComplianceAuditor;
import com.agent.riskcontrol.api.ContentSafetyChecker;
import com.agent.riskcontrol.api.PermissionChecker;
import com.agent.riskcontrol.model.ContentViolation;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RiskControlGrpcService unit test covering all 3 RPCs.
 *
 * <p>Uses mock API implementations with real mapper + GrpcExceptionAdvice.
 * Capturing StreamObserver to capture onNext/onError.
 */
@DisplayName("RiskControlGrpcService gRPC service")
class RiskControlGrpcServiceTest {

    private ContentSafetyChecker contentSafetyChecker;
    private PermissionChecker permissionChecker;
    private ComplianceAuditor complianceAuditor;
    private RiskControlGrpcService grpcService;

    @BeforeEach
    void setUp() {
        contentSafetyChecker = mock(ContentSafetyChecker.class);
        permissionChecker = mock(PermissionChecker.class);
        complianceAuditor = mock(ComplianceAuditor.class);
        RiskControlMapper mapper = new RiskControlMapper();
        GrpcExceptionAdvice advice = new GrpcExceptionAdvice();
        grpcService = new RiskControlGrpcService(
                contentSafetyChecker, permissionChecker, complianceAuditor, mapper, advice);
    }

    // ===== RPC 1: CheckContent =====

    @Test
    @DisplayName("Should_CheckContent_When_SafeContent: safe content returns no violations")
    void should_CheckContent_When_SafeContent() {
        // given
        CheckContentRequest req = CheckContentRequest.newBuilder()
                .setContent("Hello, this is a normal message.")
                .setContentType("text")
                .build();
        when(contentSafetyChecker.check(any(com.agent.riskcontrol.model.CheckContentRequest.class)))
                .thenReturn(new com.agent.riskcontrol.model.CheckContentResponse(true, new ArrayList<>(), "Hello, this is a normal message."));

        // when
        CapturingObserver<CheckContentResponse> observer = new CapturingObserver<>();
        grpcService.checkContent(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        assertThat(observer.error).isNull();
        assertThat(observer.values).hasSize(1);
        CheckContentResponse resp = observer.values.get(0);
        assertThat(resp.getSafe()).isTrue();
        assertThat(resp.getViolationsCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should_CheckContent_When_UnsafeContent: unsafe content returns violations")
    void should_CheckContent_When_UnsafeContent() {
        // given
        CheckContentRequest req = CheckContentRequest.newBuilder()
                .setContent("call me at 13800138000")
                .setContentType("text")
                .addCheckCategories("pii")
                .build();
        List<ContentViolation> violations = List.of(
                new ContentViolation("pii", "high", "Phone number detected", 11)
        );
        when(contentSafetyChecker.check(any(com.agent.riskcontrol.model.CheckContentRequest.class)))
                .thenReturn(new com.agent.riskcontrol.model.CheckContentResponse(false, violations, "call me at 1**********"));

        // when
        CapturingObserver<CheckContentResponse> observer = new CapturingObserver<>();
        grpcService.checkContent(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        CheckContentResponse resp = observer.values.get(0);
        assertThat(resp.getSafe()).isFalse();
        assertThat(resp.getViolationsCount()).isEqualTo(1);
        assertThat(resp.getViolations(0).getCategory()).isEqualTo("pii");
        assertThat(resp.getSanitizedContent()).isEqualTo("call me at 1**********");
    }

    // ===== RPC 2: CheckPermission =====

    @Test
    @DisplayName("Should_CheckPermission_When_Allowed: admin user is allowed")
    void should_CheckPermission_When_Allowed() {
        // given
        CheckPermissionRequest req = CheckPermissionRequest.newBuilder()
                .setUserId("admin-001")
                .setAction("delete")
                .setResource("agent-001")
                .setResourceType("agent")
                .build();
        when(permissionChecker.check(any(com.agent.riskcontrol.model.CheckPermissionRequest.class)))
                .thenReturn(new com.agent.riskcontrol.model.CheckPermissionResponse(true, "Role admin permits action delete", List.of("admin")));

        // when
        CapturingObserver<CheckPermissionResponse> observer = new CapturingObserver<>();
        grpcService.checkPermission(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        CheckPermissionResponse resp = observer.values.get(0);
        assertThat(resp.getAllowed()).isTrue();
        assertThat(resp.getReason()).contains("admin");
        assertThat(resp.getRequiredRolesList()).contains("admin");
    }

    @Test
    @DisplayName("Should_CheckPermission_When_Denied: viewer user denied for create action")
    void should_CheckPermission_When_Denied() {
        // given
        CheckPermissionRequest req = CheckPermissionRequest.newBuilder()
                .setUserId("viewer-001")
                .setAction("create")
                .setResource("tool-001")
                .setResourceType("tool")
                .build();
        when(permissionChecker.check(any(com.agent.riskcontrol.model.CheckPermissionRequest.class)))
                .thenReturn(new com.agent.riskcontrol.model.CheckPermissionResponse(false,
                        "Role viewer does not permit action create", List.of("admin", "user")));

        // when
        CapturingObserver<CheckPermissionResponse> observer = new CapturingObserver<>();
        grpcService.checkPermission(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        CheckPermissionResponse resp = observer.values.get(0);
        assertThat(resp.getAllowed()).isFalse();
        assertThat(resp.getReason()).contains("viewer");
        assertThat(resp.getRequiredRolesList()).containsExactly("admin", "user");
    }

    // ===== RPC 3: AuditLog =====

    @Test
    @DisplayName("Should_AuditLog_When_ValidRequest: record audit returns audit_id")
    void should_AuditLog_When_ValidRequest() {
        // given
        AuditLogRequest req = AuditLogRequest.newBuilder()
                .setAction("execute")
                .setActorId("user-001")
                .setResourceType("tool")
                .setResourceId("tool-001")
                .setResult("success")
                .setDetail("Tool executed successfully")
                .build();
        when(complianceAuditor.record(any(com.agent.riskcontrol.model.AuditLogRequest.class)))
                .thenReturn(new com.agent.riskcontrol.model.AuditLogAck("audit-abc123"));

        // when
        CapturingObserver<AuditLogAck> observer = new CapturingObserver<>();
        grpcService.auditLog(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        AuditLogAck ack = observer.values.get(0);
        assertThat(ack.getAuditId()).isEqualTo("audit-abc123");
    }

    @Test
    @DisplayName("Should_ThrowError_When_AuditLogFails: audit exception returns INTERNAL")
    void should_ThrowError_When_AuditLogFails() {
        // given
        AuditLogRequest req = AuditLogRequest.newBuilder()
                .setAction("")
                .setActorId("user-001")
                .build();
        when(complianceAuditor.record(any(com.agent.riskcontrol.model.AuditLogRequest.class)))
                .thenThrow(new com.agent.riskcontrol.exception.AuditException("Audit action must not be empty"));

        // when
        CapturingObserver<AuditLogAck> observer = new CapturingObserver<>();
        grpcService.auditLog(req, observer);

        // then
        assertThat(observer.completed).isFalse();
        assertThat(observer.error).isNotNull();
        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
    }

    // ===== Helper =====

    /** Capturing StreamObserver for testing gRPC responses. */
    private static class CapturingObserver<T> implements StreamObserver<T> {
        final List<T> values = new ArrayList<>();
        Throwable error;
        boolean completed;

        @Override
        public void onNext(T value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable t) {
            error = t;
        }

        @Override
        public void onCompleted() {
            completed = true;
        }
    }
}
