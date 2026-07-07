package com.agent.drift.grpc;

import agentplatform.drift.v1.CorrectDriftRequest;
import agentplatform.drift.v1.CorrectDriftResponse;
import agentplatform.drift.v1.DetectDriftRequest;
import agentplatform.drift.v1.DetectDriftResponse;
import agentplatform.drift.v1.GetBaselineRequest;
import agentplatform.drift.v1.GetBaselineResponse;
import agentplatform.drift.v1.RecordBehaviorAck;
import agentplatform.drift.v1.RecordBehaviorRequest;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.drift.api.BaselineAnchor;
import com.agent.drift.api.DriftCorrector;
import com.agent.drift.api.DriftDetector;
import com.agent.drift.config.DriftMonitorProperties;
import com.agent.drift.entity.BehaviorBaselineEntity;
import com.agent.drift.enums.DriftType;
import com.agent.drift.model.BehaviorBaseline;
import com.agent.drift.model.DriftCorrectAction;
import com.agent.drift.model.DriftSignal;
import com.agent.drift.repository.BehaviorBaselineRepository;
import com.agent.drift.repository.DriftSignalRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DriftGrpcService} unit test (4 RPC normal + error flows).
 *
 * <p>Uses mock dependencies, real {@link DriftMapper} + real {@link GrpcExceptionAdvice},
 * capturing StreamObserver to verify onNext/onError.</p>
 */
@DisplayName("DriftGrpcService gRPC service")
class DriftGrpcServiceTest {

    private DriftDetector driftDetector;
    private DriftCorrector driftCorrector;
    private BaselineAnchor baselineAnchor;
    private BehaviorBaselineRepository baselineRepository;
    private DriftSignalRepository signalRepository;
    private DriftGrpcService grpcService;
    private DriftMonitorProperties properties;

    @BeforeEach
    void setUp() {
        driftDetector = mock(DriftDetector.class);
        driftCorrector = mock(DriftCorrector.class);
        baselineAnchor = mock(BaselineAnchor.class);
        baselineRepository = mock(BehaviorBaselineRepository.class);
        signalRepository = mock(DriftSignalRepository.class);
        DriftMapper mapper = new DriftMapper();
        GrpcExceptionAdvice advice = new GrpcExceptionAdvice();
        properties = new DriftMonitorProperties();
        grpcService = new DriftGrpcService(
                driftDetector, driftCorrector, baselineAnchor,
                baselineRepository, signalRepository, mapper, advice, properties);
    }

    // ===== RPC 1: DetectDrift =====

    @Test
    @DisplayName("Should_DetectNoDrift_When_SignalNormal: no drift detected")
    void should_DetectNoDrift_When_SignalNormal() {
        DetectDriftRequest req = DetectDriftRequest.newBuilder()
                .setAgentId(1L)
                .setSessionId("session-001")
                .setDriftType("behavior")
                .build();
        when(driftDetector.detect(any(DriftSignal.class))).thenReturn(DriftType.NONE);

        CapturingObserver<DetectDriftResponse> observer = new CapturingObserver<>();
        grpcService.detectDrift(req, observer);

        assertThat(observer.completed).isTrue();
        assertThat(observer.error).isNull();
        assertThat(observer.values).hasSize(1);
        DetectDriftResponse resp = observer.values.get(0);
        assertThat(resp.getDriftDetected()).isFalse();
        assertThat(resp.getDriftLevel()).isEqualTo("none");
        verify(signalRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should_DetectDrift_When_SignalExceedsThreshold: drift detected and persisted")
    void should_DetectDrift_When_SignalExceedsThreshold() {
        DetectDriftRequest req = DetectDriftRequest.newBuilder()
                .setAgentId(1L)
                .setSessionId("session-001")
                .setDriftType("behavior")
                .build();
        when(driftDetector.detect(any(DriftSignal.class))).thenReturn(DriftType.BEHAVIOR_DRIFT);

        com.agent.drift.entity.DriftSignalEntity savedEntity = new com.agent.drift.entity.DriftSignalEntity();
        savedEntity.setSignalId("signal-uuid-001");
        savedEntity.setAgentId("1");
        savedEntity.setDriftType("BEHAVIOR_DRIFT");
        when(signalRepository.save(any(com.agent.drift.entity.DriftSignalEntity.class)))
                .thenReturn(savedEntity);

        CapturingObserver<DetectDriftResponse> observer = new CapturingObserver<>();
        grpcService.detectDrift(req, observer);

        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        DetectDriftResponse resp = observer.values.get(0);
        assertThat(resp.getDriftDetected()).isTrue();
        verify(signalRepository).save(any(com.agent.drift.entity.DriftSignalEntity.class));
    }

    @Test
    @DisplayName("Should_ThrowInvalidArgument_When_AgentIdInvalid: invalid agent_id -> error")
    void should_ThrowInvalidArgument_When_AgentIdInvalid() {
        DetectDriftRequest req = DetectDriftRequest.newBuilder()
                .setAgentId(0L)
                .build();

        CapturingObserver<DetectDriftResponse> observer = new CapturingObserver<>();
        grpcService.detectDrift(req, observer);

        assertThat(observer.completed).isFalse();
        assertThat(observer.error).isNotNull();
        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    // ===== RPC 2: CorrectDrift =====

    @Test
    @DisplayName("Should_CorrectDrift_When_SessionLevel: session-level correction")
    void should_CorrectDrift_When_SessionLevel() {
        CorrectDriftRequest req = CorrectDriftRequest.newBuilder()
                .setAgentId(1L)
                .setDriftType("behavior")
                .setCorrectionStrategy("adjust")
                .setContext("correct behavior drift")
                .build();
        when(driftCorrector.correct(any(DriftCorrectAction.class))).thenReturn(true);

        CapturingObserver<CorrectDriftResponse> observer = new CapturingObserver<>();
        grpcService.correctDrift(req, observer);

        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        CorrectDriftResponse resp = observer.values.get(0);
        assertThat(resp.getCorrectionId()).isNotEmpty();
        assertThat(resp.getActionTaken()).isNotEmpty();
        verify(driftCorrector).correct(any(DriftCorrectAction.class));
    }

    @Test
    @DisplayName("Should_CorrectDriftFail_When_CorrectorReturnsFalse: correction failed")
    void should_CorrectDriftFail_When_CorrectorReturnsFalse() {
        CorrectDriftRequest req = CorrectDriftRequest.newBuilder()
                .setAgentId(1L)
                .setDriftType("alignment")
                .setCorrectionStrategy("reset")
                .build();
        when(driftCorrector.correct(any(DriftCorrectAction.class))).thenReturn(false);

        CapturingObserver<CorrectDriftResponse> observer = new CapturingObserver<>();
        grpcService.correctDrift(req, observer);

        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        assertThat(observer.values.get(0).getActionTaken()).isEqualTo("no_action");
    }

    // ===== RPC 3: GetBaseline =====

    @Test
    @DisplayName("Should_GetBaseline_When_Exists: return baseline for agent")
    void should_GetBaseline_When_Exists() {
        GetBaselineRequest req = GetBaselineRequest.newBuilder()
                .setAgentId(1L)
                .setBaselineType("behavior")
                .build();
        BehaviorBaselineEntity entity = new BehaviorBaselineEntity();
        entity.setAgentId("1");
        entity.setBaselineType("behavior");
        entity.setBaselineValue(0.85);
        entity.setObservationCount(10);
        when(baselineRepository.findByAgentIdAndBaselineType("1", "behavior"))
                .thenReturn(Optional.of(entity));

        CapturingObserver<GetBaselineResponse> observer = new CapturingObserver<>();
        grpcService.getBaseline(req, observer);

        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        GetBaselineResponse resp = observer.values.get(0);
        assertThat(resp.getBaselineType()).isEqualTo("behavior");
        assertThat(resp.getBaselineValue()).isEqualTo(0.85);
        assertThat(resp.getObservationCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("Should_ThrowNotFound_When_BaselineNotExists: baseline not found -> error")
    void should_ThrowNotFound_When_BaselineNotExists() {
        GetBaselineRequest req = GetBaselineRequest.newBuilder()
                .setAgentId(99L)
                .setBaselineType("behavior")
                .build();
        when(baselineRepository.findByAgentIdAndBaselineType("99", "behavior"))
                .thenReturn(Optional.empty());

        CapturingObserver<GetBaselineResponse> observer = new CapturingObserver<>();
        grpcService.getBaseline(req, observer);

        assertThat(observer.completed).isFalse();
        assertThat(observer.error).isNotNull();
        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    // ===== RPC 4: RecordBehavior =====

    @Test
    @DisplayName("Should_RecordBehavior_When_NewBaseline: create new baseline")
    void should_RecordBehavior_When_NewBaseline() {
        RecordBehaviorRequest req = RecordBehaviorRequest.newBuilder()
                .setAgentId(1L)
                .setSessionId("session-001")
                .setBehaviorType("task_completion")
                .setMetricValue(0.9)
                .setContext("test context")
                .build();
        when(baselineRepository.findByAgentIdAndBaselineType("1", "task_completion"))
                .thenReturn(Optional.empty());
        when(baselineRepository.save(any(BehaviorBaselineEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(baselineAnchor.anchor(any(BehaviorBaseline.class))).thenReturn(true);

        CapturingObserver<RecordBehaviorAck> observer = new CapturingObserver<>();
        grpcService.recordBehavior(req, observer);

        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        RecordBehaviorAck ack = observer.values.get(0);
        assertThat(ack.getRecordId()).isNotEmpty();
        assertThat(ack.getBaselineUpdated()).isTrue();
        verify(baselineRepository).save(any(BehaviorBaselineEntity.class));
        verify(baselineAnchor).anchor(any(BehaviorBaseline.class));
    }

    @Test
    @DisplayName("Should_RecordBehavior_When_ExistingBaseline: update existing baseline")
    void should_RecordBehavior_When_ExistingBaseline() {
        RecordBehaviorRequest req = RecordBehaviorRequest.newBuilder()
                .setAgentId(1L)
                .setSessionId("session-001")
                .setBehaviorType("task_completion")
                .setMetricValue(0.8)
                .build();
        BehaviorBaselineEntity existing = new BehaviorBaselineEntity();
        existing.setAgentId("1");
        existing.setBaselineType("task_completion");
        existing.setBaselineValue(0.85);
        existing.setObservationCount(10);
        when(baselineRepository.findByAgentIdAndBaselineType("1", "task_completion"))
                .thenReturn(Optional.of(existing));
        when(baselineRepository.save(any(BehaviorBaselineEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CapturingObserver<RecordBehaviorAck> observer = new CapturingObserver<>();
        grpcService.recordBehavior(req, observer);

        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        assertThat(observer.values.get(0).getBaselineUpdated()).isTrue();
        verify(baselineRepository).save(any(BehaviorBaselineEntity.class));
        verify(baselineAnchor, never()).anchor(any());
    }

    @Test
    @DisplayName("Should_ThrowInvalidArgument_When_RecordBehaviorAgentIdInvalid: invalid agent_id -> error")
    void should_ThrowInvalidArgument_When_RecordBehaviorAgentIdInvalid() {
        RecordBehaviorRequest req = RecordBehaviorRequest.newBuilder()
                .setAgentId(0L)
                .setMetricValue(0.5)
                .build();

        CapturingObserver<RecordBehaviorAck> observer = new CapturingObserver<>();
        grpcService.recordBehavior(req, observer);

        assertThat(observer.completed).isFalse();
        assertThat(observer.error).isNotNull();
        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    // ===== Capturing Observer =====

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
