package com.agent.drift.grpc;

import agentplatform.drift.v1.CorrectDriftRequest;
import agentplatform.drift.v1.CorrectDriftResponse;
import agentplatform.drift.v1.DetectDriftRequest;
import agentplatform.drift.v1.DetectDriftResponse;
import agentplatform.drift.v1.DriftSignal;
import agentplatform.drift.v1.GetBaselineRequest;
import agentplatform.drift.v1.GetBaselineResponse;
import agentplatform.drift.v1.RecordBehaviorAck;
import agentplatform.drift.v1.RecordBehaviorRequest;
import com.agent.drift.entity.BehaviorBaselineEntity;
import com.agent.drift.entity.DriftSignalEntity;
import com.agent.drift.model.BehaviorBaseline;
import com.agent.drift.model.DriftCorrectAction;
import com.agent.drift.enums.DriftLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Proto <-> domain model mapper for drift-monitor gRPC service.
 *
 * <p>Responsible for converting gRPC proto messages to domain models and vice versa.</p>
 */
@Component
public class DriftMapper {

    // ===== DetectDrift =====

    /**
     * Convert DetectDriftRequest proto to domain DriftSignal.
     */
    public com.agent.drift.model.DriftSignal toDriftSignal(DetectDriftRequest req) {
        com.agent.drift.model.DriftSignal signal = new com.agent.drift.model.DriftSignal();
        if (!req.getDriftType().isEmpty()) {
            try {
                signal.setType(com.agent.drift.enums.DriftType.valueOf(req.getDriftType().toUpperCase()));
            } catch (IllegalArgumentException e) {
                signal.setType(com.agent.drift.enums.DriftType.NONE);
            }
        }
        signal.setAgentId(String.valueOf(req.getAgentId()));
        return signal;
    }

    /**
     * Build DetectDriftResponse proto from drift detection result.
     */
    public DetectDriftResponse toDetectDriftResponse(boolean driftDetected, String driftType,
                                                      String driftLevel, String description,
                                                      List<DriftSignalEntity> signals) {
        DetectDriftResponse.Builder builder = DetectDriftResponse.newBuilder()
                .setDriftDetected(driftDetected)
                .setDriftType(driftType == null ? "none" : driftType)
                .setDriftLevel(driftLevel == null ? "none" : driftLevel)
                .setDescription(description == null ? "" : description);
        if (signals != null) {
            for (DriftSignalEntity entity : signals) {
                builder.addSignals(toProtoDriftSignal(entity));
            }
        }
        return builder.build();
    }

    /**
     * Convert DriftSignalEntity to proto DriftSignal.
     */
    public DriftSignal toProtoDriftSignal(DriftSignalEntity entity) {
        DriftSignal.Builder builder = DriftSignal.newBuilder();
        if (entity.getSignalId() != null) {
            builder.setSignalId(entity.getSignalId());
        }
        if (entity.getIndicator() != null) {
            builder.setIndicator(entity.getIndicator());
        }
        if (entity.getObservedValue() != null) {
            builder.setObservedValue(entity.getObservedValue());
        }
        if (entity.getExpectedValue() != null) {
            builder.setExpectedValue(entity.getExpectedValue());
        }
        if (entity.getDeviation() != null) {
            builder.setDeviation(entity.getDeviation());
        }
        return builder.build();
    }

    // ===== CorrectDrift =====

    /**
     * Convert CorrectDriftRequest proto to domain DriftCorrectAction.
     */
    public DriftCorrectAction toDriftCorrectAction(CorrectDriftRequest req) {
        DriftLevel level = "system".equalsIgnoreCase(req.getCorrectionStrategy())
                ? DriftLevel.SYSTEM : DriftLevel.SESSION;
        DriftCorrectAction action = new DriftCorrectAction(level, String.valueOf(req.getAgentId()));
        action.setCoreConstraintSummary(req.getContext());
        action.setTargetVersion(req.getCorrectionStrategy());
        return action;
    }

    /**
     * Build CorrectDriftResponse proto.
     */
    public CorrectDriftResponse toCorrectDriftResponse(String correctionId, String actionTaken,
                                                         String detail) {
        return CorrectDriftResponse.newBuilder()
                .setCorrectionId(correctionId == null ? "" : correctionId)
                .setActionTaken(actionTaken == null ? "" : actionTaken)
                .setDetail(detail == null ? "" : detail)
                .build();
    }

    // ===== GetBaseline =====

    /**
     * Build GetBaselineResponse from entity.
     */
    public GetBaselineResponse toGetBaselineResponse(BehaviorBaselineEntity entity) {
        GetBaselineResponse.Builder builder = GetBaselineResponse.newBuilder()
                .setAgentId(entity.getAgentId() == null ? 0 : Long.parseLong(entity.getAgentId()));
        if (entity.getBaselineType() != null) {
            builder.setBaselineType(entity.getBaselineType());
        }
        if (entity.getBaselineValue() != null) {
            builder.setBaselineValue(entity.getBaselineValue());
        }
        if (entity.getObservationCount() != null) {
            builder.setObservationCount(entity.getObservationCount());
        }
        if (entity.getLastUpdated() != null) {
            builder.setLastUpdated(entity.getLastUpdated().toEpochMilli());
        }
        if (entity.getParameters() != null) {
            // Simple: split comma-separated parameters
            for (String param : entity.getParameters().split(",")) {
                String trimmed = param.trim();
                if (!trimmed.isEmpty()) {
                    builder.addParameters(trimmed);
                }
            }
        }
        return builder.build();
    }

    // ===== RecordBehavior =====

    /**
     * Convert RecordBehaviorRequest to BehaviorBaselineEntity (for update/create).
     */
    public BehaviorBaselineEntity toBehaviorBaselineEntity(RecordBehaviorRequest req) {
        BehaviorBaselineEntity entity = new BehaviorBaselineEntity();
        entity.setAgentId(String.valueOf(req.getAgentId()));
        entity.setBaselineType(req.getBehaviorType());
        entity.setBaselineValue(req.getMetricValue());
        entity.setObservationCount(1);
        entity.setParameters(req.getContext());
        return entity;
    }

    /**
     * Build RecordBehaviorAck proto.
     */
    public RecordBehaviorAck toRecordBehaviorAck(String recordId, boolean baselineUpdated) {
        return RecordBehaviorAck.newBuilder()
                .setRecordId(recordId == null ? "" : recordId)
                .setBaselineUpdated(baselineUpdated)
                .build();
    }

    // ===== Utility =====

    /**
     * Convert domain DriftSignal to DriftSignalEntity for persistence.
     */
    public DriftSignalEntity toDriftSignalEntity(com.agent.drift.model.DriftSignal signal,
                                                   String sessionId, String driftLevel) {
        DriftSignalEntity entity = new DriftSignalEntity();
        entity.setSignalId(UUID.randomUUID().toString());
        entity.setAgentId(signal.getAgentId());
        entity.setSessionId(sessionId);
        entity.setDriftType(signal.getType() == null ? "none" : signal.getType().name());
        entity.setDriftLevel(driftLevel);
        entity.setObservedValue(signal.getScore());
        entity.setExpectedValue(signal.getThreshold());
        return entity;
    }

    /**
     * Convert domain BehaviorBaseline to BehaviorBaselineEntity for persistence.
     */
    public BehaviorBaselineEntity toBehaviorBaselineEntity(BehaviorBaseline baseline) {
        BehaviorBaselineEntity entity = new BehaviorBaselineEntity();
        entity.setAgentId(baseline.getAgentId());
        entity.setBaselineType("behavior");
        entity.setBaselineValue(baseline.getBaselineSuccessRate());
        entity.setObservationCount(0);
        entity.setParameters(baseline.getGoldenSetHash());
        return entity;
    }
}
