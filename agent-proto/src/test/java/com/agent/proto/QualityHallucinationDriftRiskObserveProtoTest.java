package com.agent.proto;

import agentplatform.quality.v1.*;
import agentplatform.hallucination.v1.*;
import agentplatform.drift.v1.*;
import agentplatform.riskcontrol.v1.*;
import agentplatform.observability.v1.*;
import agentplatform.common.v1.TraceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QualityHallucinationDriftRiskObserveProtoTest {

    private static TraceContext trace() {
        return TraceContext.newBuilder()
                .setTenantId(1L).setUserId("u_1").setSessionId("s_1")
                .setTaskId("t_1").setTraceId("tr_1").setSpanId("sp_1")
                .build();
    }

    // ---- Quality ----

    @Test
    @DisplayName("ValidateTaskRequest should round-trip with all fields")
    void should_RoundTripValidateTaskRequest() throws Exception {
        ValidateTaskRequest req = ValidateTaskRequest.newBuilder()
                .setTaskId("tk_001").setAgentId("ag_001")
                .setResultJson("{\"output\":\"hello\"}")
                .setScene("code_gen")
                .addValidationLayers("hard")
                .addValidationLayers("consistency")
                .setTrace(trace())
                .build();
        ValidateTaskRequest parsed = ValidateTaskRequest.parseFrom(req.toByteArray());
        assertThat(parsed.getTaskId()).isEqualTo("tk_001");
        assertThat(parsed.getScene()).isEqualTo("code_gen");
        assertThat(parsed.getValidationLayersCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("ReportBadcaseRequest should carry category and severity")
    void should_CarryCategoryAndSeverity_When_ReportBadcase() throws Exception {
        ReportBadcaseRequest req = ReportBadcaseRequest.newBuilder()
                .setTaskId("tk_002").setAgentId("ag_002")
                .setCategory("hallucination").setSeverity("high")
                .setDescription("factual error").setTrace(trace())
                .build();
        ReportBadcaseRequest parsed = ReportBadcaseRequest.parseFrom(req.toByteArray());
        assertThat(parsed.getCategory()).isEqualTo("hallucination");
        assertThat(parsed.getSeverity()).isEqualTo("high");
    }

    // ---- Hallucination ----

    @Test
    @DisplayName("SelfCheckResponse should carry result and confidence")
    void should_CarryResultAndConfidence_When_SelfCheck() throws Exception {
        SelfCheckResponse resp = SelfCheckResponse.newBuilder()
                .setResult("verified").setConfidence(0.95)
                .setReason("source confirms claim")
                .build();
        SelfCheckResponse parsed = SelfCheckResponse.parseFrom(resp.toByteArray());
        assertThat(parsed.getResult()).isEqualTo("verified");
        assertThat(parsed.getConfidence()).isEqualTo(0.95, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("GuardToolCallResponse should carry guard_result and risks")
    void should_CarryGuardResultAndRisks_When_GuardToolCall() throws Exception {
        GuardToolCallResponse resp = GuardToolCallResponse.newBuilder()
                .setGuardResult("warn").setReason("potentially destructive")
                .addRisks("data_loss").build();
        GuardToolCallResponse parsed = GuardToolCallResponse.parseFrom(resp.toByteArray());
        assertThat(parsed.getGuardResult()).isEqualTo("warn");
        assertThat(parsed.getRisksCount()).isEqualTo(1);
    }

    // ---- Drift ----

    @Test
    @DisplayName("DetectDriftResponse should carry drift_detected and signals")
    void should_CarryDriftDetectedAndSignals_When_DetectDrift() throws Exception {
        DetectDriftResponse resp = DetectDriftResponse.newBuilder()
                .setDriftDetected(true).setDriftType("behavior")
                .setDriftLevel("moderate")
                .addSignals(DriftSignal.newBuilder()
                        .setSignalId("sig_1").setIndicator("response_length")
                        .setObservedValue(1500.0).setExpectedValue(800.0)
                        .setDeviation(0.875).build())
                .build();
        DetectDriftResponse parsed = DetectDriftResponse.parseFrom(resp.toByteArray());
        assertThat(parsed.getDriftDetected()).isTrue();
        assertThat(parsed.getSignalsCount()).isEqualTo(1);
        assertThat(parsed.getSignals(0).getDeviation()).isEqualTo(0.875, org.assertj.core.data.Offset.offset(0.001));
    }

    // ---- Risk Control ----

    @Test
    @DisplayName("CheckContentResponse should carry safe flag and violations")
    void should_CarrySafeAndViolations_When_CheckContent() throws Exception {
        CheckContentResponse resp = CheckContentResponse.newBuilder()
                .setSafe(false)
                .addViolations(ContentViolation.newBuilder()
                        .setCategory("pii").setSeverity("high")
                        .setDetail("phone number detected").setPosition(42).build())
                .setSanitizedContent("my number is ***")
                .build();
        CheckContentResponse parsed = CheckContentResponse.parseFrom(resp.toByteArray());
        assertThat(parsed.getSafe()).isFalse();
        assertThat(parsed.getViolationsCount()).isEqualTo(1);
        assertThat(parsed.getViolations(0).getCategory()).isEqualTo("pii");
    }

    @Test
    @DisplayName("CheckPermissionResponse should carry allowed flag and reason")
    void should_CarryAllowedAndReason_When_CheckPermission() throws Exception {
        CheckPermissionResponse resp = CheckPermissionResponse.newBuilder()
                .setAllowed(false).setReason("insufficient role")
                .addRequiredRoles("admin").build();
        CheckPermissionResponse parsed = CheckPermissionResponse.parseFrom(resp.toByteArray());
        assertThat(parsed.getAllowed()).isFalse();
        assertThat(parsed.getRequiredRolesCount()).isEqualTo(1);
    }

    // ---- Observability ----

    @Test
    @DisplayName("GetHealthResponse should carry overall_status and service health")
    void should_CarryOverallStatusAndServices_When_GetHealth() throws Exception {
        GetHealthResponse resp = GetHealthResponse.newBuilder()
                .setOverallStatus("degraded")
                .addServices(ServiceHealth.newBuilder()
                        .setServiceName("agent-runtime").setStatus("up")
                        .setUptimeSeconds(86400L).setErrorRate(0.01)
                        .setLatencyP95Ms(120.5).build())
                .addServices(ServiceHealth.newBuilder()
                        .setServiceName("model-gateway").setStatus("degraded")
                        .setErrorRate(0.15).build())
                .build();
        GetHealthResponse parsed = GetHealthResponse.parseFrom(resp.toByteArray());
        assertThat(parsed.getOverallStatus()).isEqualTo("degraded");
        assertThat(parsed.getServicesCount()).isEqualTo(2);
        assertThat(parsed.getServices(0).getLatencyP95Ms()).isEqualTo(120.5, org.assertj.core.data.Offset.offset(0.001));
    }
}
