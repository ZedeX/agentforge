package com.agent.planning.grpc;

import agentplatform.common.v1.TraceContext;
import agentplatform.planning.v1.AssessRequest;
import agentplatform.planning.v1.AssessResponse;
import agentplatform.planning.v1.PlanRequest;
import agentplatform.planning.v1.PlanResponse;
import agentplatform.planning.v1.ReplanRequest;
import agentplatform.planning.v1.ValidateResponse;
import com.agent.planning.enums.PlanComplexity;
import com.agent.planning.model.Plan;
import com.agent.planning.model.PlanValidationResult;
import com.agent.planning.model.PlanningContext;
import com.agent.planning.model.ReplanContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PlanningProtoMapper}.
 */
@DisplayName("PlanningProtoMapper")
class PlanningProtoMapperTest {

    private PlanningProtoMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PlanningProtoMapper();
    }

    // ===== AssessRequest -> PlanningContext =====

    @Test
    @DisplayName("Should_MapAssessRequest_When_AllFieldsPresent")
    void should_MapAssessRequest_When_AllFieldsPresent() {
        AssessRequest req = AssessRequest.newBuilder()
                .setTaskId("task-001")
                .setTitle("Build feature")
                .setGoal("Implement X")
                .setTrace(TraceContext.newBuilder().setTraceId("trace-1").build())
                .build();
        PlanningContext ctx = mapper.toPlanningContext(req);
        assertThat(ctx.getTaskId()).isEqualTo("task-001");
        assertThat(ctx.getGoal()).isEqualTo("Build feature");
        assertThat(ctx.getTraceId()).isEqualTo("trace-1");
    }

    @Test
    @DisplayName("Should_MapAssessRequest_When_NoTrace")
    void should_MapAssessRequest_When_NoTrace() {
        AssessRequest req = AssessRequest.newBuilder()
                .setTaskId("task-002")
                .build();
        PlanningContext ctx = mapper.toPlanningContext(req);
        assertThat(ctx.getTaskId()).isEqualTo("task-002");
        assertThat(ctx.getTraceId()).isEmpty();
    }

    // ===== PlanRequest -> PlanningContext =====

    @Test
    @DisplayName("Should_MapPlanRequest_When_AllFieldsPresent")
    void should_MapPlanRequest_When_AllFieldsPresent() {
        PlanRequest req = PlanRequest.newBuilder()
                .setTaskId("task-003")
                .setTaskSchemaJson("{\"type\":\"report\"}")
                .setPreferTemplate(true)
                .build();
        PlanningContext ctx = mapper.toPlanningContext(req);
        assertThat(ctx.getTaskId()).isEqualTo("task-003");
        assertThat(ctx.getTaskSchemaJson()).isEqualTo("{\"type\":\"report\"}");
        assertThat(ctx.isPreferTemplate()).isTrue();
    }

    // ===== ReplanRequest -> ReplanContext =====

    @Test
    @DisplayName("Should_MapReplanRequest_When_AllFieldsPresent")
    void should_MapReplanRequest_When_AllFieldsPresent() {
        ReplanRequest req = ReplanRequest.newBuilder()
                .setTaskId("task-004")
                .setReason("DAG_CYCLE_DETECTED")
                .setReplanCount(2)
                .setPreviousDagJson("{\"nodes\":[]}")
                .build();
        ReplanContext ctx = mapper.toReplanContext(req);
        assertThat(ctx.getTaskId()).isEqualTo("task-004");
        assertThat(ctx.getReason()).isEqualTo("DAG_CYCLE_DETECTED");
        assertThat(ctx.getReplanCount()).isEqualTo(2);
        assertThat(ctx.getPreviousDagJson()).isEqualTo("{\"nodes\":[]}");
    }

    // ===== Plan -> AssessResponse =====

    @Test
    @DisplayName("Should_MapPlanToAssessResponse_When_PlanL2")
    void should_MapPlanToAssessResponse_When_PlanL2() {
        Plan plan = new Plan("plan-1", "task-001");
        plan.setComplexity(PlanComplexity.L2);
        AssessResponse resp = mapper.toAssessResponse(plan);
        assertThat(resp.getComplexity()).isEqualTo(2);
        assertThat(resp.getReason()).isNotBlank();
    }

    @Test
    @DisplayName("Should_MapPlanToAssessResponse_When_PlanNull")
    void should_MapPlanToAssessResponse_When_PlanNull() {
        AssessResponse resp = mapper.toAssessResponse(null);
        assertThat(resp.getComplexity()).isEqualTo(1);
    }

    // ===== Plan -> PlanResponse =====

    @Test
    @DisplayName("Should_MapPlanToPlanResponse_When_AllFieldsSet")
    void should_MapPlanToPlanResponse_When_AllFieldsSet() {
        Plan plan = new Plan("plan-2", "task-002");
        plan.setDagJson("{\"nodes\":[{\"id\":\"n1\"}]}");
        plan.setSource("template");
        plan.setVersion(2);
        plan.setTemplateId(50L);
        PlanResponse resp = mapper.toPlanResponse(plan);
        assertThat(resp.getDagJson()).isEqualTo("{\"nodes\":[{\"id\":\"n1\"}]}");
        assertThat(resp.getSource()).isEqualTo("template");
        assertThat(resp.getDagVersion()).isEqualTo(2);
        assertThat(resp.getTemplateId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("Should_MapPlanToPlanResponse_When_PlanNull")
    void should_MapPlanToPlanResponse_When_PlanNull() {
        PlanResponse resp = mapper.toPlanResponse(null);
        assertThat(resp.getDagJson()).isEmpty();
        assertThat(resp.getDagVersion()).isEqualTo(0);
    }

    // ===== PlanValidationResult -> ValidateResponse =====

    @Test
    @DisplayName("Should_MapValidationResult_When_Valid")
    void should_MapValidationResult_When_Valid() {
        PlanValidationResult result = new PlanValidationResult(true, List.of(), List.of());
        ValidateResponse resp = mapper.toValidateResponse(result);
        assertThat(resp.getValid()).isTrue();
        assertThat(resp.getErrorsList()).isEmpty();
    }

    @Test
    @DisplayName("Should_MapValidationResult_When_InvalidWithErrors")
    void should_MapValidationResult_When_InvalidWithErrors() {
        PlanValidationResult result = new PlanValidationResult(false,
                List.of("error1"), List.of("warn1"));
        ValidateResponse resp = mapper.toValidateResponse(result);
        assertThat(resp.getValid()).isFalse();
        assertThat(resp.getErrorsList()).containsExactly("error1");
        assertThat(resp.getWarningsList()).containsExactly("warn1");
    }

    @Test
    @DisplayName("Should_MapValidationResult_When_Null")
    void should_MapValidationResult_When_Null() {
        ValidateResponse resp = mapper.toValidateResponse(null);
        assertThat(resp.getValid()).isFalse();
    }
}
