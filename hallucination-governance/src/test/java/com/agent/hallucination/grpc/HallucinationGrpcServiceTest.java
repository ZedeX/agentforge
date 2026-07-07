package com.agent.hallucination.grpc;

import agentplatform.hallucination.v1.AnchorRagRequest;
import agentplatform.hallucination.v1.AnchorRagResponse;
import agentplatform.hallucination.v1.GuardToolCallRequest;
import agentplatform.hallucination.v1.GuardToolCallResponse;
import agentplatform.hallucination.v1.RecordMetricAck;
import agentplatform.hallucination.v1.RecordMetricRequest;
import agentplatform.hallucination.v1.SelfCheckRequest;
import agentplatform.hallucination.v1.SelfCheckResponse;
import com.agent.hallucination.api.HallucinationMetricWriter;
import com.agent.hallucination.api.RagAnchor;
import com.agent.hallucination.api.SelfCheckEngine;
import com.agent.hallucination.api.ToolGatewayGuard;
import com.agent.hallucination.enums.GuardResult;
import com.agent.hallucination.enums.SelfCheckResult;
import com.agent.hallucination.model.Claim;
import com.agent.hallucination.model.HallucinationMetric;
import com.agent.hallucination.model.ToolCallGuardRequest;
import com.agent.hallucination.entity.HallucinationMetricEntity;
import com.agent.hallucination.repository.HallucinationMetricRepository;
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
 * {@link HallucinationGrpcService} 单测（覆盖 4 RPC 正常流 + 异常流）。
 *
 * <p>纯单测：mock 业务服务 + Repository，使用真实 {@link HallucinationMapper} + 真实 {@link GrpcExceptionAdvice}，
 * 用 capturing StreamObserver 捕获 onNext/onError。</p>
 */
@DisplayName("HallucinationGrpcService gRPC 服务测试")
class HallucinationGrpcServiceTest {

    private SelfCheckEngine selfCheckEngine;
    private ToolGatewayGuard toolGatewayGuard;
    private RagAnchor ragAnchor;
    private HallucinationMetricWriter metricWriter;
    private HallucinationMetricRepository metricRepository;
    private HallucinationGrpcService grpcService;

    @BeforeEach
    void setUp() {
        selfCheckEngine = mock(SelfCheckEngine.class);
        toolGatewayGuard = mock(ToolGatewayGuard.class);
        ragAnchor = mock(RagAnchor.class);
        metricWriter = mock(HallucinationMetricWriter.class);
        metricRepository = mock(HallucinationMetricRepository.class);
        HallucinationMapper mapper = new HallucinationMapper();
        GrpcExceptionAdvice advice = new GrpcExceptionAdvice();
        grpcService = new HallucinationGrpcService(
                selfCheckEngine, toolGatewayGuard, ragAnchor,
                metricWriter, metricRepository, mapper, advice);
    }

    // ===== RPC 1: SelfCheck =====

    @Test
    @DisplayName("Should_SelfCheck_When_ClaimValid: 正常自检 → PASS，返回 verified")
    void should_SelfCheck_When_ClaimValid() {
        // given
        SelfCheckRequest req = SelfCheckRequest.newBuilder()
                .setTaskId("task-001")
                .setAgentId("agent-001")
                .setClaim("The sky is blue")
                .setContext("weather discussion")
                .addSources("source-1")
                .build();
        when(selfCheckEngine.check(any(Claim.class))).thenReturn(SelfCheckResult.PASS);

        // when
        CapturingObserver<SelfCheckResponse> observer = new CapturingObserver<>();
        grpcService.selfCheck(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        assertThat(observer.error).isNull();
        assertThat(observer.values).hasSize(1);
        SelfCheckResponse resp = observer.values.get(0);
        assertThat(resp.getResult()).isEqualTo("verified");
        assertThat(resp.getConfidence()).isGreaterThanOrEqualTo(0.8);
        assertThat(resp.getReason()).isNotEmpty();
    }

    @Test
    @DisplayName("Should_SelfCheckSuspected_When_NoSource: 无来源标签 → SUSPECTED")
    void should_SelfCheckSuspected_When_NoSource() {
        // given
        SelfCheckRequest req = SelfCheckRequest.newBuilder()
                .setTaskId("task-002")
                .setAgentId("agent-001")
                .setClaim("绝对正确的答案")
                .build();
        when(selfCheckEngine.check(any(Claim.class))).thenReturn(SelfCheckResult.SUSPECTED);

        // when
        CapturingObserver<SelfCheckResponse> observer = new CapturingObserver<>();
        grpcService.selfCheck(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        SelfCheckResponse resp = observer.values.get(0);
        assertThat(resp.getResult()).isEqualTo("hallucinated");
    }

    @Test
    @DisplayName("Should_ThrowInvalidArgument_When_ClaimEmpty: 空 claim → INVALID_ARGUMENT")
    void should_ThrowInvalidArgument_When_ClaimEmpty() {
        // given
        SelfCheckRequest req = SelfCheckRequest.newBuilder()
                .setTaskId("task-003")
                .setAgentId("agent-001")
                .setClaim("")
                .build();

        // when
        CapturingObserver<SelfCheckResponse> observer = new CapturingObserver<>();
        grpcService.selfCheck(req, observer);

        // then
        assertThat(observer.completed).isFalse();
        assertThat(observer.error).isNotNull();
        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        verify(selfCheckEngine, never()).check(any());
    }

    // ===== RPC 2: GuardToolCall =====

    @Test
    @DisplayName("Should_GuardToolCall_When_ParamsValid: 参数合法 → ALLOWED")
    void should_GuardToolCall_When_ParamsValid() {
        // given
        GuardToolCallRequest req = GuardToolCallRequest.newBuilder()
                .setTaskId("task-001")
                .setAgentId("agent-001")
                .setToolId("web-search")
                .setInputJson("{\"query\":\"test\"}")
                .build();
        when(toolGatewayGuard.guard(any(ToolCallGuardRequest.class))).thenReturn(GuardResult.ALLOWED);

        // when
        CapturingObserver<GuardToolCallResponse> observer = new CapturingObserver<>();
        grpcService.guardToolCall(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        GuardToolCallResponse resp = observer.values.get(0);
        assertThat(resp.getGuardResult()).isEqualTo("allow");
    }

    @Test
    @DisplayName("Should_GuardToolCallBlocked_When_ParamsInvalid: 参数不合法 → REJECTED")
    void should_GuardToolCallBlocked_When_ParamsInvalid() {
        // given
        GuardToolCallRequest req = GuardToolCallRequest.newBuilder()
                .setTaskId("task-001")
                .setAgentId("agent-001")
                .setToolId("dangerous-tool")
                .setInputJson("")
                .build();
        when(toolGatewayGuard.guard(any(ToolCallGuardRequest.class))).thenReturn(GuardResult.REJECTED);

        // when
        CapturingObserver<GuardToolCallResponse> observer = new CapturingObserver<>();
        grpcService.guardToolCall(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        GuardToolCallResponse resp = observer.values.get(0);
        assertThat(resp.getGuardResult()).isEqualTo("block");
        assertThat(resp.getRisksList()).isNotEmpty();
    }

    @Test
    @DisplayName("Should_ThrowInvalidArgument_When_ToolIdEmpty: 空 tool_id → INVALID_ARGUMENT")
    void should_ThrowInvalidArgument_When_ToolIdEmpty() {
        // given
        GuardToolCallRequest req = GuardToolCallRequest.newBuilder()
                .setTaskId("task-001")
                .setAgentId("agent-001")
                .setToolId("")
                .build();

        // when
        CapturingObserver<GuardToolCallResponse> observer = new CapturingObserver<>();
        grpcService.guardToolCall(req, observer);

        // then
        assertThat(observer.completed).isFalse();
        assertThat(observer.error).isNotNull();
        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    // ===== RPC 3: AnchorRag =====

    @Test
    @DisplayName("Should_AnchorRag_When_Anchored: RAG 锚定成功 → anchored")
    void should_AnchorRag_When_Anchored() {
        // given
        AnchorRagRequest req = AnchorRagRequest.newBuilder()
                .setTaskId("task-001")
                .setResponse("The answer is 42")
                .addSourceDocs("doc-1: The answer is 42")
                .build();
        when(ragAnchor.anchor(any(String.class))).thenReturn(true);

        // when
        CapturingObserver<AnchorRagResponse> observer = new CapturingObserver<>();
        grpcService.anchorRag(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        AnchorRagResponse resp = observer.values.get(0);
        assertThat(resp.getAnchorResult()).isEqualTo("anchored");
        assertThat(resp.getAnchorScore()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Should_AnchorRagUnanchored_When_InfoInsufficient: RAG 锚定失败 → unanchored")
    void should_AnchorRagUnanchored_When_InfoInsufficient() {
        // given
        AnchorRagRequest req = AnchorRagRequest.newBuilder()
                .setTaskId("task-001")
                .setResponse("Some unverified claim")
                .build();
        when(ragAnchor.anchor(any(String.class))).thenReturn(false);

        // when
        CapturingObserver<AnchorRagResponse> observer = new CapturingObserver<>();
        grpcService.anchorRag(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        AnchorRagResponse resp = observer.values.get(0);
        assertThat(resp.getAnchorResult()).isEqualTo("unanchored");
        assertThat(resp.getAnchorScore()).isEqualTo(0.0);
        assertThat(resp.getUnsupportedClaimsList()).isNotEmpty();
    }

    @Test
    @DisplayName("Should_ThrowInvalidArgument_When_ResponseEmpty: 空 response → INVALID_ARGUMENT")
    void should_ThrowInvalidArgument_When_ResponseEmpty() {
        // given
        AnchorRagRequest req = AnchorRagRequest.newBuilder()
                .setTaskId("task-001")
                .setResponse("")
                .build();

        // when
        CapturingObserver<AnchorRagResponse> observer = new CapturingObserver<>();
        grpcService.anchorRag(req, observer);

        // then
        assertThat(observer.completed).isFalse();
        assertThat(observer.error).isNotNull();
        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    // ===== RPC 4: RecordMetric =====

    @Test
    @DisplayName("Should_RecordMetric_When_RequestValid: 正常记录指标 → RecordMetricAck")
    void should_RecordMetric_When_RequestValid() {
        // given
        RecordMetricRequest req = RecordMetricRequest.newBuilder()
                .setTaskId("task-001")
                .setAgentId("agent-001")
                .setLayer("self_check")
                .setEventType("detected")
                .setDetail("Hallucination detected in claim")
                .build();
        HallucinationMetricEntity savedEntity = new HallucinationMetricEntity();
        savedEntity.setMetricId("metric-uuid-001");
        when(metricRepository.save(any(HallucinationMetricEntity.class))).thenReturn(savedEntity);

        // when
        CapturingObserver<RecordMetricAck> observer = new CapturingObserver<>();
        grpcService.recordMetric(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        RecordMetricAck ack = observer.values.get(0);
        assertThat(ack.getMetricId()).isEqualTo("metric-uuid-001");
        verify(metricWriter).write(any(HallucinationMetric.class));
        verify(metricRepository).save(any(HallucinationMetricEntity.class));
    }

    @Test
    @DisplayName("Should_ThrowInvalidArgument_When_AgentIdEmpty: 空 agent_id → INVALID_ARGUMENT")
    void should_ThrowInvalidArgument_When_AgentIdEmpty() {
        // given
        RecordMetricRequest req = RecordMetricRequest.newBuilder()
                .setTaskId("task-001")
                .setAgentId("")
                .setLayer("self_check")
                .setEventType("detected")
                .build();

        // when
        CapturingObserver<RecordMetricAck> observer = new CapturingObserver<>();
        grpcService.recordMetric(req, observer);

        // then
        assertThat(observer.completed).isFalse();
        assertThat(observer.error).isNotNull();
        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        verify(metricRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should_ThrowInvalidArgument_When_LayerEmpty: 空 layer → INVALID_ARGUMENT")
    void should_ThrowInvalidArgument_When_LayerEmpty() {
        // given
        RecordMetricRequest req = RecordMetricRequest.newBuilder()
                .setTaskId("task-001")
                .setAgentId("agent-001")
                .setLayer("")
                .setEventType("detected")
                .build();

        // when
        CapturingObserver<RecordMetricAck> observer = new CapturingObserver<>();
        grpcService.recordMetric(req, observer);

        // then
        assertThat(observer.completed).isFalse();
        assertThat(observer.error).isNotNull();
        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    // ===== 辅助类 =====

    /** 捕获 onNext/onError/onCompleted 的 StreamObserver。 */
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
