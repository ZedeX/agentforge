package com.agent.riskcontrol.grpc;

import agentplatform.riskcontrol.v1.AuditLogAck;
import agentplatform.riskcontrol.v1.AuditLogRequest;
import agentplatform.riskcontrol.v1.CheckContentRequest;
import agentplatform.riskcontrol.v1.CheckContentResponse;
import agentplatform.riskcontrol.v1.CheckPermissionRequest;
import agentplatform.riskcontrol.v1.CheckPermissionResponse;
import agentplatform.riskcontrol.v1.ContentViolation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Proto <-> POJO mapper for RiskControl gRPC service.
 *
 * <p>Handles conversion between proto messages and internal model objects.
 */
@Component
public class RiskControlMapper {

    // ===== proto request -> POJO =====

    /**
     * Proto CheckContentRequest -> POJO CheckContentRequest.
     */
    public com.agent.riskcontrol.model.CheckContentRequest toPojo(CheckContentRequest proto) {
        return new com.agent.riskcontrol.model.CheckContentRequest(
                proto.getContent(),
                proto.getContentType(),
                new ArrayList<>(proto.getCheckCategoriesList())
        );
    }

    /**
     * Proto CheckPermissionRequest -> POJO CheckPermissionRequest.
     */
    public com.agent.riskcontrol.model.CheckPermissionRequest toPojo(CheckPermissionRequest proto) {
        return new com.agent.riskcontrol.model.CheckPermissionRequest(
                proto.getUserId(),
                proto.getResource(),
                proto.getAction(),
                proto.getResourceType()
        );
    }

    /**
     * Proto AuditLogRequest -> POJO AuditLogRequest.
     */
    public com.agent.riskcontrol.model.AuditLogRequest toPojo(AuditLogRequest proto) {
        return new com.agent.riskcontrol.model.AuditLogRequest(
                proto.getAction(),
                proto.getActorId(),
                proto.getResourceType(),
                proto.getResourceId(),
                proto.getResult(),
                proto.getDetail()
        );
    }

    // ===== POJO -> proto =====

    /**
     * POJO CheckContentResponse -> proto CheckContentResponse.
     */
    public CheckContentResponse toProto(com.agent.riskcontrol.model.CheckContentResponse pojo) {
        CheckContentResponse.Builder builder = CheckContentResponse.newBuilder()
                .setSafe(pojo.isSafe())
                .setSanitizedContent(nullToEmpty(pojo.getSanitizedContent()));

        for (com.agent.riskcontrol.model.ContentViolation v : pojo.getViolations()) {
            builder.addViolations(ContentViolation.newBuilder()
                    .setCategory(nullToEmpty(v.getCategory()))
                    .setSeverity(nullToEmpty(v.getSeverity()))
                    .setDetail(nullToEmpty(v.getDetail()))
                    .setPosition(v.getPosition())
                    .build());
        }

        return builder.build();
    }

    /**
     * POJO CheckPermissionResponse -> proto CheckPermissionResponse.
     */
    public CheckPermissionResponse toProto(com.agent.riskcontrol.model.CheckPermissionResponse pojo) {
        CheckPermissionResponse.Builder builder = CheckPermissionResponse.newBuilder()
                .setAllowed(pojo.isAllowed())
                .setReason(nullToEmpty(pojo.getReason()));

        for (String role : pojo.getRequiredRoles()) {
            builder.addRequiredRoles(role);
        }

        return builder.build();
    }

    /**
     * POJO AuditLogAck -> proto AuditLogAck.
     */
    public AuditLogAck toProto(com.agent.riskcontrol.model.AuditLogAck pojo) {
        return AuditLogAck.newBuilder()
                .setAuditId(nullToEmpty(pojo.getAuditId()))
                .build();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
