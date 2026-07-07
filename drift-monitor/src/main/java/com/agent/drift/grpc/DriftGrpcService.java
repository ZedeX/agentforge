package com.agent.drift.grpc;

import agentplatform.drift.v1.CorrectDriftRequest;
import agentplatform.drift.v1.CorrectDriftResponse;
import agentplatform.drift.v1.DetectDriftRequest;
import agentplatform.drift.v1.DetectDriftResponse;
import agentplatform.drift.v1.DriftMonitorServiceGrpc;
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
import com.agent.drift.entity.DriftSignalEntity;
import com.agent.drift.enums.DriftLevel;
import com.agent.drift.enums.DriftType;
import com.agent.drift.model.BehaviorBaseline;
import com.agent.drift.model.DriftCorrectAction;
import com.agent.drift.repository.BehaviorBaselineRepository;
import com.agent.drift.repository.DriftSignalRepository;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Drift Monitor gRPC service implementation (F11 behavior/effect/alignment/memory drift, port 8108).
 *
 * <p>Implements 4 RPCs: DetectDrift, CorrectDrift, GetBaseline, RecordBehavior.</p>
 *
 * <p>Responsibilities: proto request -> call domain service -> mapper converts to proto response -> deliver via observer.
 * Exceptions translated to gRPC Status via {@link GrpcExceptionAdvice}.</p>
 */
@Slf4j
@GrpcService
public class DriftGrpcService extends DriftMonitorServiceGrpc.DriftMonitorServiceImplBase {

    private final DriftDetector driftDetector;
    private final DriftCorrector driftCorrector;
    private final BaselineAnchor baselineAnchor;
    private final BehaviorBaselineRepository baselineRepository;
    private final DriftSignalRepository signalRepository;
    private final DriftMapper mapper;
    private final GrpcExceptionAdvice exceptionAdvice;
    private final DriftMonitorProperties properties;

    public DriftGrpcService(DriftDetector driftDetector,
                             DriftCorrector driftCorrector,
                             BaselineAnchor baselineAnchor,
                             BehaviorBaselineRepository baselineRepository,
                             DriftSignalRepository signalRepository,
                             DriftMapper mapper,
                             GrpcExceptionAdvice exceptionAdvice,
                             DriftMonitorProperties properties) {
        this.driftDetector = driftDetector;
        this.driftCorrector = driftCorrector;
        this.baselineAnchor = baselineAnchor;
        this.baselineRepository = baselineRepository;
        this.signalRepository = signalRepository;
        this.mapper = mapper;
        this.exceptionAdvice = exceptionAdvice;
        this.properties = properties;
    }

    // ===== RPC 1: DetectDrift =====

    @Override
    public void detectDrift(DetectDriftRequest request,
                            StreamObserver<DetectDriftResponse> responseObserver) {
        try {
            if (request.getAgentId() <= 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "agent_id must be positive");
            }
            com.agent.drift.model.DriftSignal signal = mapper.toDriftSignal(request);
            // Set threshold from properties based on drift type
            signal.setThreshold(properties.getDetection().getThresholdModerate());

            DriftType detected = driftDetector.detect(signal);
            boolean driftDetected = detected != DriftType.NONE;
            String driftLevel = driftDetected ? classifyDriftLevel(signal.getScore()) : "none";
            String description = driftDetected
                    ? "Drift detected: " + detected.name()
                    : "No drift detected";

            // Persist detected signal to JPA
            DriftSignalEntity signalEntity = null;
            if (driftDetected) {
                signalEntity = mapper.toDriftSignalEntity(signal, request.getSessionId(), driftLevel);
                signalEntity = signalRepository.save(signalEntity);
            }

            List<DriftSignalEntity> signalEntities = signalEntity != null
                    ? List.of(signalEntity) : new ArrayList<>();
            DetectDriftResponse resp = mapper.toDetectDriftResponse(
                    driftDetected, detected.name().toLowerCase(), driftLevel,
                    description, signalEntities);
            log.info("detectDrift agent_id={} detected={} level={}",
                    request.getAgentId(), driftDetected, driftLevel);
            responseObserver.onNext(resp);
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }

    // ===== RPC 2: CorrectDrift =====

    @Override
    public void correctDrift(CorrectDriftRequest request,
                             StreamObserver<CorrectDriftResponse> responseObserver) {
        try {
            if (request.getAgentId() <= 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "agent_id must be positive");
            }
            DriftCorrectAction action = mapper.toDriftCorrectAction(request);
            boolean success = driftCorrector.correct(action);
            String correctionId = UUID.randomUUID().toString();
            String actionTaken = success ? mapActionTaken(action.getLevel()) : "no_action";
            String detail = success ? "Correction applied successfully" : "Correction failed";
            CorrectDriftResponse resp = mapper.toCorrectDriftResponse(correctionId, actionTaken, detail);
            log.info("correctDrift agent_id={} strategy={} success={}",
                    request.getAgentId(), request.getCorrectionStrategy(), success);
            responseObserver.onNext(resp);
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }

    // ===== RPC 3: GetBaseline =====

    @Override
    public void getBaseline(GetBaselineRequest request,
                            StreamObserver<GetBaselineResponse> responseObserver) {
        try {
            if (request.getAgentId() <= 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "agent_id must be positive");
            }
            String agentId = String.valueOf(request.getAgentId());
            String baselineType = request.getBaselineType().isEmpty() ? "behavior" : request.getBaselineType();
            Optional<BehaviorBaselineEntity> found = baselineRepository.findByAgentIdAndBaselineType(
                    agentId, baselineType);
            if (found.isEmpty()) {
                throw new BusinessException(ErrorCode.AGENT_NOT_FOUND,
                        "baseline not found for agent=" + agentId + " type=" + baselineType);
            }
            GetBaselineResponse resp = mapper.toGetBaselineResponse(found.get());
            log.info("getBaseline agent_id={} type={}", request.getAgentId(), baselineType);
            responseObserver.onNext(resp);
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }

    // ===== RPC 4: RecordBehavior =====

    @Override
    public void recordBehavior(RecordBehaviorRequest request,
                               StreamObserver<RecordBehaviorAck> responseObserver) {
        try {
            if (request.getAgentId() <= 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "agent_id must be positive");
            }
            String agentId = String.valueOf(request.getAgentId());
            String behaviorType = request.getBehaviorType().isEmpty() ? "task_completion" : request.getBehaviorType();

            // Upsert baseline in JPA
            Optional<BehaviorBaselineEntity> existing = baselineRepository.findByAgentIdAndBaselineType(
                    agentId, behaviorType);
            boolean baselineUpdated;
            String recordId = UUID.randomUUID().toString();

            if (existing.isPresent()) {
                BehaviorBaselineEntity entity = existing.get();
                // Update running average
                int count = entity.getObservationCount() == null ? 0 : entity.getObservationCount();
                double oldValue = entity.getBaselineValue() == null ? 0.0 : entity.getBaselineValue();
                double newValue = (oldValue * count + request.getMetricValue()) / (count + 1);
                entity.setBaselineValue(newValue);
                entity.setObservationCount(count + 1);
                baselineRepository.save(entity);
                baselineUpdated = true;
            } else {
                BehaviorBaselineEntity entity = mapper.toBehaviorBaselineEntity(request);
                baselineRepository.save(entity);
                // Also anchor via in-memory BaselineAnchor
                BehaviorBaseline baseline = new BehaviorBaseline(agentId, "1.0", "");
                baseline.setBaselineSuccessRate(request.getMetricValue());
                baselineAnchor.anchor(baseline);
                baselineUpdated = true;
            }

            RecordBehaviorAck ack = mapper.toRecordBehaviorAck(recordId, baselineUpdated);
            log.info("recordBehavior agent_id={} type={} updated={}",
                    request.getAgentId(), behaviorType, baselineUpdated);
            responseObserver.onNext(ack);
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }

    // ===== Utility =====

    private String classifyDriftLevel(double score) {
        if (score >= properties.getDetection().getThresholdSevere()) {
            return "severe";
        } else if (score >= properties.getDetection().getThresholdModerate()) {
            return "moderate";
        } else if (score >= properties.getDetection().getThresholdSlight()) {
            return "slight";
        }
        return "none";
    }

    private String mapActionTaken(DriftLevel level) {
        return level == DriftLevel.SYSTEM ? "system_rollback" : "baseline_reset";
    }
}
