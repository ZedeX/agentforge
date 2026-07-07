package com.agent.planning.grpc;

import agentplatform.common.v1.TraceContext;
import agentplatform.planning.v1.AssessRequest;
import agentplatform.planning.v1.AssessResponse;
import agentplatform.planning.v1.PlanRequest;
import agentplatform.planning.v1.PlanResponse;
import agentplatform.planning.v1.ReplanRequest;
import agentplatform.planning.v1.ValidateRequest;
import agentplatform.planning.v1.ValidateResponse;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.planning.api.PlanningService;
import com.agent.planning.enums.PlanComplexity;
import com.agent.planning.enums.PlanStatus;
import com.agent.planning.model.Plan;
import com.agent.planning.model.PlanValidationResult;
import com.agent.planning.model.PlanningContext;
import com.agent.planning.model.ReplanContext;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PlanningGrpcService}.
 *
 * <p>Tests all 4 RPCs with mock PlanningService + real mapper + real GrpcExceptionAdvice.
 * Uses CapturingObserver to capture onNext/onError results.</p>
 */
@DisplayName("PlanningGrpcService gRPC service")
class PlanningGrpcServiceTest {

    private PlanningService planningService;
    private PlanningGrpcService grpcService;

    @BeforeEach
    void setUp() {
        planningService = mock(PlanningService.class);
        PlanningProtoMapper mapper = new PlanningProtoMapper();
        GrpcExceptionAdvice advice = new GrpcExceptionAdvice();
        grpcService = new PlanningGrpcService(planningService, mapper, advice);
    }

    // ===== RPC 1: AssessComplexity =====

    @Test
    @DisplayName("Should_AssessComplexity_When_TaskIdValid: normal assess -> returns complexity")
    void should_AssessComplexity_When_TaskIdValid() {
        // given
        AssessRequest req = AssessRequest.newBuilder()
                .setTaskId("task-001")
                .setTitle("Generate weekly report")
                .setGoal("Create a summary")
                .build();
        Plan plan = new Plan("plan-1", "task-001");
        plan.setComplexity(PlanComplexity.L2);
        when(planningService.assessComplexity(any(PlanningContext.class))).thenReturn(plan);

        // when
        CapturingObserver<AssessResponse> observer = new CapturingObserver<>();
        grpcService.assessComplexity(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        assertThat(observer.error).isNull();
        assertThat(observer.values).hasSize(1);
        AssessResponse resp = observer.values.get(0);
        assertThat(resp.getComplexity()).isEqualTo(2);
        verify(planningService).assessComplexity(any(PlanningContext.class));
    }

    @Test
    @DisplayName("Should_ThrowInvalidArgument_When_TaskIdEmpty_Assess: empty task_id -> INVALID_ARGUMENT")
    void should_ThrowInvalidArgument_When_TaskIdEmpty_Assess() {
        AssessRequest req = AssessRequest.newBuilder()
                .setTaskId("")
                .setTitle("test")
                .build();

        CapturingObserver<AssessResponse> observer = new CapturingObserver<>();
        grpcService.assessComplexity(req, observer);

        assertThat(observer.completed).isFalse();
        assertThat(observer.error).isNotNull();
        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        verify(planningService, never()).assessComplexity(any());
    }

    // ===== RPC 2: Plan =====

    @Test
    @DisplayName("Should_Plan_When_TaskIdValid: normal plan -> returns PlanResponse")
    void should_Plan_When_TaskIdValid() {
        PlanRequest req = PlanRequest.newBuilder()
                .setTaskId("task-002")
                .setTaskSchemaJson("{\"type\":\"report\"}")
                .setPreferTemplate(true)
                .build();
        Plan plan = new Plan("plan-2", "task-002");
        plan.setDagJson("{\"nodes\":[{\"id\":\"n1\"}]}");
        plan.setSource("template");
        plan.setVersion(1);
        plan.setTemplateId(100L);
        when(planningService.plan(any(PlanningContext.class))).thenReturn(plan);

        CapturingObserver<PlanResponse> observer = new CapturingObserver<>();
        grpcService.plan(req, observer);

        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        PlanResponse resp = observer.values.get(0);
        assertThat(resp.getDagJson()).isEqualTo("{\"nodes\":[{\"id\":\"n1\"}]}");
        assertThat(resp.getSource()).isEqualTo("template");
        assertThat(resp.getDagVersion()).isEqualTo(1);
        assertThat(resp.getTemplateId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Should_ThrowInvalidArgument_When_TaskIdEmpty_Plan: empty task_id -> INVALID_ARGUMENT")
    void should_ThrowInvalidArgument_When_TaskIdEmpty_Plan() {
        PlanRequest req = PlanRequest.newBuilder()
                .setTaskId("")
                .build();

        CapturingObserver<PlanResponse> observer = new CapturingObserver<>();
        grpcService.plan(req, observer);

        assertThat(observer.completed).isFalse();
        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    // ===== RPC 3: ValidatePlan =====

    @Test
    @DisplayName("Should_ValidatePlan_When_DagValid: valid DAG -> returns valid=true")
    void should_ValidatePlan_When_DagValid() {
        ValidateRequest req = ValidateRequest.newBuilder()
                .setTaskId("task-003")
                .setDagJson("{\"nodes\":[{\"id\":\"n1\"}],\"edges\":[]}")
                .build();
        PlanValidationResult result = new PlanValidationResult(true, List.of(), List.of());
        when(planningService.validatePlan(any(Plan.class), any(PlanningContext.class))).thenReturn(result);

        CapturingObserver<ValidateResponse> observer = new CapturingObserver<>();
        grpcService.validatePlan(req, observer);

        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        ValidateResponse resp = observer.values.get(0);
        assertThat(resp.getValid()).isTrue();
        assertThat(resp.getErrorsList()).isEmpty();
        assertThat(resp.getWarningsList()).isEmpty();
    }

    @Test
    @DisplayName("Should_ValidatePlan_When_DagInvalid: invalid DAG -> returns valid=false with errors")
    void should_ValidatePlan_When_DagInvalid() {
        ValidateRequest req = ValidateRequest.newBuilder()
                .setTaskId("task-003")
                .setDagJson("{\"nodes\":[]}")
                .build();
        PlanValidationResult result = new PlanValidationResult(false,
                List.of("DAG has no nodes"), List.of("low efficiency"));
        when(planningService.validatePlan(any(Plan.class), any(PlanningContext.class))).thenReturn(result);

        CapturingObserver<ValidateResponse> observer = new CapturingObserver<>();
        grpcService.validatePlan(req, observer);

        assertThat(observer.completed).isTrue();
        ValidateResponse resp = observer.values.get(0);
        assertThat(resp.getValid()).isFalse();
        assertThat(resp.getErrorsList()).containsExactly("DAG has no nodes");
        assertThat(resp.getWarningsList()).containsExactly("low efficiency");
    }

    @Test
    @DisplayName("Should_ThrowInvalidArgument_When_DagJsonEmpty: empty dag_json -> INVALID_ARGUMENT")
    void should_ThrowInvalidArgument_When_DagJsonEmpty() {
        ValidateRequest req = ValidateRequest.newBuilder()
                .setTaskId("task-003")
                .setDagJson("")
                .build();

        CapturingObserver<ValidateResponse> observer = new CapturingObserver<>();
        grpcService.validatePlan(req, observer);

        assertThat(observer.completed).isFalse();
        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    // ===== RPC 4: Replan =====

    @Test
    @DisplayName("Should_Replan_When_TaskIdValid: normal replan -> returns new PlanResponse")
    void should_Replan_When_TaskIdValid() {
        ReplanRequest req = ReplanRequest.newBuilder()
                .setTaskId("task-004")
                .setReason("DAG_CYCLE_DETECTED")
                .setReplanCount(1)
                .setPreviousDagJson("{\"nodes\":[{\"id\":\"n1\"}]}")
                .build();
        Plan plan = new Plan("plan-4", "task-004");
        plan.setDagJson("{\"nodes\":[{\"id\":\"n1\"}]}_v2_replan");
        plan.setSource("ai");
        plan.setVersion(3);
        plan.setReplanCount(2);
        when(planningService.replan(any(ReplanContext.class))).thenReturn(plan);

        CapturingObserver<PlanResponse> observer = new CapturingObserver<>();
        grpcService.replan(req, observer);

        assertThat(observer.completed).isTrue();
        PlanResponse resp = observer.values.get(0);
        assertThat(resp.getSource()).isEqualTo("ai");
        assertThat(resp.getDagVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should_ReturnManual_When_ReplanNull: MANUAL mode -> returns source=manual")
    void should_ReturnManual_When_ReplanNull() {
        ReplanRequest req = ReplanRequest.newBuilder()
                .setTaskId("task-004")
                .setReason("COMPLETENESS_FAIL")
                .setReplanCount(5)
                .build();
        when(planningService.replan(any(ReplanContext.class))).thenReturn(null);

        CapturingObserver<PlanResponse> observer = new CapturingObserver<>();
        grpcService.replan(req, observer);

        assertThat(observer.completed).isTrue();
        PlanResponse resp = observer.values.get(0);
        assertThat(resp.getSource()).isEqualTo("manual");
    }

    @Test
    @DisplayName("Should_ThrowInvalidArgument_When_TaskIdEmpty_Replan: empty task_id -> INVALID_ARGUMENT")
    void should_ThrowInvalidArgument_When_TaskIdEmpty_Replan() {
        ReplanRequest req = ReplanRequest.newBuilder()
                .setTaskId("")
                .setReason("test")
                .build();

        CapturingObserver<PlanResponse> observer = new CapturingObserver<>();
        grpcService.replan(req, observer);

        assertThat(observer.completed).isFalse();
        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    // ===== CapturingObserver =====

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
