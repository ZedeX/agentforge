package com.agent.planning.grpc;

import agentplatform.planning.v1.AssessRequest;
import agentplatform.planning.v1.AssessResponse;
import agentplatform.planning.v1.PlanRequest;
import agentplatform.planning.v1.PlanResponse;
import agentplatform.planning.v1.PlanningServiceGrpc;
import agentplatform.planning.v1.ReplanRequest;
import agentplatform.planning.v1.ValidateRequest;
import agentplatform.planning.v1.ValidateResponse;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.planning.api.PlanningService;
import com.agent.planning.model.Plan;
import com.agent.planning.model.PlanValidationResult;
import com.agent.planning.model.PlanningContext;
import com.agent.planning.model.ReplanContext;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * PlanningService gRPC server implementation.
 *
 * <p>Covers 4 RPCs from planning.proto:
 * {@code AssessComplexity} / {@code Plan} / {@code ValidatePlan} / {@code Replan}.</p>
 *
 * <p>Responsibility: proto request -> call domain service -> {@link PlanningProtoMapper} for
 * proto response -> dispatch to observer.
 * Exceptions translated via {@link GrpcExceptionAdvice}.</p>
 */
@Slf4j
@GrpcService
public class PlanningGrpcService extends PlanningServiceGrpc.PlanningServiceImplBase {

    private final PlanningService planningService;
    private final PlanningProtoMapper mapper;
    private final GrpcExceptionAdvice exceptionAdvice;

    public PlanningGrpcService(PlanningService planningService,
                                PlanningProtoMapper mapper,
                                GrpcExceptionAdvice exceptionAdvice) {
        this.planningService = planningService;
        this.mapper = mapper;
        this.exceptionAdvice = exceptionAdvice;
    }

    // ===== RPC 1: AssessComplexity =====

    @Override
    public void assessComplexity(AssessRequest request,
                                  StreamObserver<AssessResponse> responseObserver) {
        try {
            if (request.getTaskId() == null || request.getTaskId().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "task_id must not be empty");
            }
            PlanningContext ctx = mapper.toPlanningContext(request);
            Plan plan = planningService.assessComplexity(ctx);
            AssessResponse response = mapper.toAssessResponse(plan);
            log.info("assessComplexity success task_id={} complexity={}",
                    request.getTaskId(), plan != null ? plan.getComplexity() : "null");
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Throwable t) {
            exceptionAdvice.translate(t, responseObserver);
        }
    }

    // ===== RPC 2: Plan =====

    @Override
    public void plan(PlanRequest request,
                     StreamObserver<PlanResponse> responseObserver) {
        try {
            if (request.getTaskId() == null || request.getTaskId().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "task_id must not be empty");
            }
            PlanningContext ctx = mapper.toPlanningContext(request);
            Plan plan = planningService.plan(ctx);
            PlanResponse response = mapper.toPlanResponse(plan);
            log.info("plan success task_id={} source={}",
                    request.getTaskId(), plan != null ? plan.getSource() : "null");
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Throwable t) {
            exceptionAdvice.translate(t, responseObserver);
        }
    }

    // ===== RPC 3: ValidatePlan =====

    @Override
    public void validatePlan(ValidateRequest request,
                              StreamObserver<ValidateResponse> responseObserver) {
        try {
            if (request.getDagJson() == null || request.getDagJson().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "dag_json must not be empty");
            }
            // Build a minimal Plan from the request for validation
            Plan plan = new Plan();
            plan.setTaskId(nullToEmpty(request.getTaskId()));
            plan.setDagJson(request.getDagJson());
            PlanningContext ctx = new PlanningContext();
            ctx.setTaskId(nullToEmpty(request.getTaskId()));
            if (request.getTrace() != null) {
                ctx.setTraceId(nullToEmpty(request.getTrace().getTraceId()));
            }
            PlanValidationResult result = planningService.validatePlan(plan, ctx);
            ValidateResponse response = mapper.toValidateResponse(result);
            log.info("validatePlan success task_id={} valid={}",
                    request.getTaskId(), result.isPassed());
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Throwable t) {
            exceptionAdvice.translate(t, responseObserver);
        }
    }

    // ===== RPC 4: Replan =====

    @Override
    public void replan(ReplanRequest request,
                       StreamObserver<PlanResponse> responseObserver) {
        try {
            if (request.getTaskId() == null || request.getTaskId().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "task_id must not be empty");
            }
            ReplanContext ctx = mapper.toReplanContext(request);
            Plan plan = planningService.replan(ctx);
            if (plan == null) {
                // MANUAL mode: no auto replan, return empty response
                PlanResponse response = PlanResponse.newBuilder()
                        .setDagJson("")
                        .setDagVersion(0)
                        .setSource("manual")
                        .build();
                log.info("replan requires manual intervention task_id={}", request.getTaskId());
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }
            PlanResponse response = mapper.toPlanResponse(plan);
            log.info("replan success task_id={} version={}",
                    request.getTaskId(), plan.getVersion());
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Throwable t) {
            exceptionAdvice.translate(t, responseObserver);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
