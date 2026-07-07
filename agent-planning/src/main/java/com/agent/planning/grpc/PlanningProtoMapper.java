package com.agent.planning.grpc;

import agentplatform.planning.v1.AssessRequest;
import agentplatform.planning.v1.AssessResponse;
import agentplatform.planning.v1.PlanRequest;
import agentplatform.planning.v1.PlanResponse;
import agentplatform.planning.v1.ReplanRequest;
import agentplatform.planning.v1.ValidateRequest;
import agentplatform.planning.v1.ValidateResponse;
import com.agent.planning.enums.PlanComplexity;
import com.agent.planning.model.Plan;
import com.agent.planning.model.PlanValidationResult;
import com.agent.planning.model.PlanningContext;
import com.agent.planning.model.ReplanContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Proto <-> POJO mapper for the Planning gRPC service.
 *
 * <p>Handles bidirectional conversion between gRPC proto messages
 * and domain model objects:</p>
 * <ul>
 *   <li>{@link AssessRequest} -> {@link PlanningContext}</li>
 *   <li>{@link Plan} -> {@link AssessResponse}</li>
 *   <li>{@link PlanRequest} -> {@link PlanningContext}</li>
 *   <li>{@link Plan} -> {@link PlanResponse}</li>
 *   <li>{@link ValidateRequest} -> domain objects</li>
 *   <li>{@link PlanValidationResult} -> {@link ValidateResponse}</li>
 *   <li>{@link ReplanRequest} -> {@link ReplanContext}</li>
 * </ul>
 */
@Component
public class PlanningProtoMapper {

    // ===== proto request -> domain context =====

    /**
     * Convert AssessRequest to PlanningContext.
     */
    public PlanningContext toPlanningContext(AssessRequest req) {
        PlanningContext ctx = new PlanningContext();
        ctx.setTaskId(nullToEmpty(req.getTaskId()));
        ctx.setGoal(nullToEmpty(req.getTitle()));
        if (req.getTrace() != null) {
            ctx.setTraceId(nullToEmpty(req.getTrace().getTraceId()));
        }
        return ctx;
    }

    /**
     * Convert PlanRequest to PlanningContext.
     */
    public PlanningContext toPlanningContext(PlanRequest req) {
        PlanningContext ctx = new PlanningContext();
        ctx.setTaskId(nullToEmpty(req.getTaskId()));
        ctx.setTaskSchemaJson(nullToEmpty(req.getTaskSchemaJson()));
        ctx.setPreferTemplate(req.getPreferTemplate());
        if (req.getTrace() != null) {
            ctx.setTraceId(nullToEmpty(req.getTrace().getTraceId()));
        }
        return ctx;
    }

    /**
     * Convert ReplanRequest to ReplanContext.
     */
    public ReplanContext toReplanContext(ReplanRequest req) {
        ReplanContext ctx = new ReplanContext();
        ctx.setTaskId(nullToEmpty(req.getTaskId()));
        ctx.setReason(nullToEmpty(req.getReason()));
        ctx.setReplanCount(req.getReplanCount());
        ctx.setPreviousDagJson(nullToEmpty(req.getPreviousDagJson()));
        if (req.getTrace() != null) {
            ctx.setTraceId(nullToEmpty(req.getTrace().getTraceId()));
        }
        return ctx;
    }

    // ===== domain model -> proto response =====

    /**
     * Convert Plan to AssessResponse.
     */
    public AssessResponse toAssessResponse(Plan plan) {
        if (plan == null) {
            return AssessResponse.newBuilder()
                    .setComplexity(1)
                    .setReason("unknown")
                    .build();
        }
        AssessResponse.Builder builder = AssessResponse.newBuilder()
                .setComplexity(plan.getComplexity() == null ? 1 : plan.getComplexity().getNumeric())
                .setReason(plan.getComplexity() == null ? "L1" : plan.getComplexity().getDescription());
        return builder.build();
    }

    /**
     * Convert Plan to PlanResponse.
     */
    public PlanResponse toPlanResponse(Plan plan) {
        if (plan == null) {
            return PlanResponse.newBuilder()
                    .setDagJson("")
                    .setDagVersion(0)
                    .setSource("unknown")
                    .build();
        }
        PlanResponse.Builder builder = PlanResponse.newBuilder()
                .setDagJson(nullToEmpty(plan.getDagJson()))
                .setDagVersion(plan.getVersion())
                .setSource(nullToEmpty(plan.getSource()));
        if (plan.getTemplateId() != null) {
            builder.setTemplateId(plan.getTemplateId());
        }
        return builder.build();
    }

    /**
     * Convert PlanValidationResult to ValidateResponse.
     */
    public ValidateResponse toValidateResponse(PlanValidationResult result) {
        if (result == null) {
            return ValidateResponse.newBuilder()
                    .setValid(false)
                    .build();
        }
        ValidateResponse.Builder builder = ValidateResponse.newBuilder()
                .setValid(result.isPassed());
        builder.addAllErrors(result.getErrors() != null ? result.getErrors() : List.of());
        builder.addAllWarnings(result.getWarnings() != null ? result.getWarnings() : List.of());
        return builder.build();
    }

    // ===== utility =====

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
