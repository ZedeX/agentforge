package com.agent.riskcontrol.grpc;

import agentplatform.riskcontrol.v1.AuditLogAck;
import agentplatform.riskcontrol.v1.AuditLogRequest;
import agentplatform.riskcontrol.v1.CheckContentRequest;
import agentplatform.riskcontrol.v1.CheckContentResponse;
import agentplatform.riskcontrol.v1.CheckPermissionRequest;
import agentplatform.riskcontrol.v1.CheckPermissionResponse;
import agentplatform.riskcontrol.v1.RiskControlServiceGrpc;
import com.agent.riskcontrol.api.ComplianceAuditor;
import com.agent.riskcontrol.api.ContentSafetyChecker;
import com.agent.riskcontrol.api.PermissionChecker;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * RiskControl gRPC service implementation.
 *
 * <p>3 RPCs: checkContent, checkPermission, auditLog.
 * Follows unmarshal -> delegate -> marshal pattern with GrpcExceptionAdvice.
 */
@Slf4j
@GrpcService
public class RiskControlGrpcService extends RiskControlServiceGrpc.RiskControlServiceImplBase {

    private final ContentSafetyChecker contentSafetyChecker;
    private final PermissionChecker permissionChecker;
    private final ComplianceAuditor complianceAuditor;
    private final RiskControlMapper mapper;
    private final GrpcExceptionAdvice exceptionAdvice;

    public RiskControlGrpcService(ContentSafetyChecker contentSafetyChecker,
                                   PermissionChecker permissionChecker,
                                   ComplianceAuditor complianceAuditor,
                                   RiskControlMapper mapper,
                                   GrpcExceptionAdvice exceptionAdvice) {
        this.contentSafetyChecker = contentSafetyChecker;
        this.permissionChecker = permissionChecker;
        this.complianceAuditor = complianceAuditor;
        this.mapper = mapper;
        this.exceptionAdvice = exceptionAdvice;
    }

    // ===== RPC 1: CheckContent =====

    @Override
    public void checkContent(CheckContentRequest request,
                             StreamObserver<CheckContentResponse> responseObserver) {
        if (request.getContent() == null || request.getContent().isEmpty()) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("content is required")
                    .asRuntimeException());
            return;
        }
        if (request.getContentType() == null || request.getContentType().isEmpty()) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("contentType is required")
                    .asRuntimeException());
            return;
        }
        try {
            log.info("checkContent request: contentType={} categoriesCount={}",
                    request.getContentType(), request.getCheckCategoriesCount());

            // unmarshal
            com.agent.riskcontrol.model.CheckContentRequest pojoRequest = mapper.toPojo(request);

            // delegate
            com.agent.riskcontrol.model.CheckContentResponse pojoResponse = contentSafetyChecker.check(pojoRequest);

            // marshal
            CheckContentResponse proto = mapper.toProto(pojoResponse);

            log.info("checkContent result: safe={} violationsCount={}",
                    pojoResponse.isSafe(), pojoResponse.getViolations().size());

            responseObserver.onNext(proto);
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }

    // ===== RPC 2: CheckPermission =====

    @Override
    public void checkPermission(CheckPermissionRequest request,
                                StreamObserver<CheckPermissionResponse> responseObserver) {
        if (request.getUserId() == null || request.getUserId().isEmpty()) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("userId is required")
                    .asRuntimeException());
            return;
        }
        if (request.getAction() == null || request.getAction().isEmpty()) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("action is required")
                    .asRuntimeException());
            return;
        }
        try {
            log.info("checkPermission request: userId={} action={} resource={}/{}",
                    request.getUserId(), request.getAction(),
                    request.getResourceType(), request.getResource());

            // unmarshal
            com.agent.riskcontrol.model.CheckPermissionRequest pojoRequest = mapper.toPojo(request);

            // delegate
            com.agent.riskcontrol.model.CheckPermissionResponse pojoResponse = permissionChecker.check(pojoRequest);

            // marshal
            CheckPermissionResponse proto = mapper.toProto(pojoResponse);

            log.info("checkPermission result: allowed={} reason={}",
                    pojoResponse.isAllowed(), pojoResponse.getReason());

            responseObserver.onNext(proto);
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }

    // ===== RPC 3: AuditLog =====

    @Override
    public void auditLog(AuditLogRequest request,
                         StreamObserver<AuditLogAck> responseObserver) {
        if (request.getActorId() == null || request.getActorId().isEmpty()) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("actorId is required")
                    .asRuntimeException());
            return;
        }
        if (request.getAction() == null || request.getAction().isEmpty()) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("action is required")
                    .asRuntimeException());
            return;
        }
        try {
            log.info("auditLog request: action={} actorId={} resource={}/{} result={}",
                    request.getAction(), request.getActorId(),
                    request.getResourceType(), request.getResourceId(), request.getResult());

            // unmarshal
            com.agent.riskcontrol.model.AuditLogRequest pojoRequest = mapper.toPojo(request);

            // delegate
            com.agent.riskcontrol.model.AuditLogAck pojoAck = complianceAuditor.record(pojoRequest);

            // marshal
            AuditLogAck proto = mapper.toProto(pojoAck);

            log.info("auditLog result: auditId={}", pojoAck.getAuditId());

            responseObserver.onNext(proto);
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }
}
