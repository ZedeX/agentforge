package com.agent.hallucination.grpc;

import agentplatform.hallucination.v1.AnchorRagRequest;
import agentplatform.hallucination.v1.AnchorRagResponse;
import agentplatform.hallucination.v1.GuardToolCallRequest;
import agentplatform.hallucination.v1.GuardToolCallResponse;
import agentplatform.hallucination.v1.RecordMetricAck;
import agentplatform.hallucination.v1.RecordMetricRequest;
import agentplatform.hallucination.v1.SelfCheckRequest;
import agentplatform.hallucination.v1.SelfCheckResponse;
import com.agent.hallucination.enums.GuardResult;
import com.agent.hallucination.enums.HallucinationLayer;
import com.agent.hallucination.enums.SelfCheckResult;
import com.agent.hallucination.model.Claim;
import com.agent.hallucination.model.HallucinationMetric;
import com.agent.hallucination.model.ToolCallGuardRequest;
import com.agent.hallucination.entity.HallucinationMetricEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Proto ↔ POJO 映射器（F10 幻觉治理 gRPC 服务）。
 *
 * <p>负责 gRPC proto 消息与领域模型 / JPA Entity 之间的双向转换。</p>
 *
 * <p>字段映射约定：</p>
 * <ul>
 *   <li>{@code result}（proto string）↔ {@link SelfCheckResult} 枚举（大写）</li>
 *   <li>{@code guard_result}（proto string）↔ {@link GuardResult} 枚举（大写）</li>
 *   <li>{@code layer}（proto string）↔ {@link HallucinationLayer} 枚举（大写）</li>
 * </ul>
 */
@Component
public class HallucinationMapper {

    // ===== SelfCheck: proto request → domain model =====

    /**
     * SelfCheckRequest → Claim。
     */
    public Claim toClaim(SelfCheckRequest req) {
        Claim claim = new Claim();
        claim.setClaimId(UUID.randomUUID().toString());
        claim.setText(req.getClaim());
        claim.setScene(req.getContext());
        // 简化: 如果 sources 非空则认为有来源标签
        claim.setHasSourceTag(!req.getSourcesList().isEmpty());
        if (!req.getSourcesList().isEmpty()) {
            claim.setSourceRef(req.getSources(0));
        }
        return claim;
    }

    /**
     * SelfCheckResult → SelfCheckResponse。
     */
    public SelfCheckResponse toSelfCheckResponse(SelfCheckResult result, Claim claim) {
        SelfCheckResponse.Builder builder = SelfCheckResponse.newBuilder()
                .setResult(formatSelfCheckResult(result))
                .setConfidence(result == SelfCheckResult.PASS ? 0.9 : (result == SelfCheckResult.SUSPECTED ? 0.5 : 0.2))
                .setReason(buildSelfCheckReason(result, claim));
        if (result == SelfCheckResult.SUSPECTED) {
            builder.addConflictingSources(claim.getText());
        }
        return builder.build();
    }

    // ===== GuardToolCall: proto request → domain model =====

    /**
     * GuardToolCallRequest → ToolCallGuardRequest。
     */
    public ToolCallGuardRequest toToolCallGuardRequest(GuardToolCallRequest req) {
        Map<String, Object> params = new HashMap<>();
        if (!req.getInputJson().isEmpty()) {
            params.put("raw_input", req.getInputJson());
        }
        List<String> requiredFields = new ArrayList<>();
        if (!req.getExpectedOutputPattern().isEmpty()) {
            requiredFields.add("raw_input");
        }
        ToolCallGuardRequest guardReq = new ToolCallGuardRequest(req.getToolId(), params);
        guardReq.setRequiredFields(requiredFields);
        return guardReq;
    }

    /**
     * GuardResult → GuardToolCallResponse。
     */
    public GuardToolCallResponse toGuardToolCallResponse(GuardResult result, String toolId) {
        GuardToolCallResponse.Builder builder = GuardToolCallResponse.newBuilder()
                .setGuardResult(formatGuardResult(result))
                .setReason(result == GuardResult.ALLOWED
                        ? "Tool call parameters validated successfully"
                        : "Tool call parameters validation failed");
        if (result == GuardResult.REJECTED) {
            builder.addRisks("Parameter schema mismatch for tool: " + toolId);
        }
        return builder.build();
    }

    // ===== AnchorRag: proto request → domain model / proto response =====

    /**
     * AnchorRagRequest → factual task description。
     */
    public String toFactualTask(AnchorRagRequest req) {
        return req.getResponse();
    }

    /**
     * 构造 AnchorRagResponse。
     */
    public AnchorRagResponse toAnchorRagResponse(boolean anchored, String response,
                                                   List<String> sourceDocs) {
        String anchorResult = anchored ? "anchored" : "unanchored";
        double anchorScore = anchored ? computeAnchorScore(response, sourceDocs) : 0.0;
        AnchorRagResponse.Builder builder = AnchorRagResponse.newBuilder()
                .setAnchorResult(anchorResult)
                .setAnchorScore(anchorScore);
        if (!anchored) {
            builder.addUnsupportedClaims(response);
        }
        return builder.build();
    }

    // ===== RecordMetric: proto request → domain model / JPA entity =====

    /**
     * RecordMetricRequest → HallucinationMetric。
     */
    public HallucinationMetric toMetric(RecordMetricRequest req) {
        HallucinationMetric metric = new HallucinationMetric();
        metric.setAgentId(req.getAgentId());
        metric.setHallucinationRate(0.0);
        metric.setTotalClaims(1);
        if ("detected".equals(req.getEventType()) || "prevented".equals(req.getEventType())) {
            metric.setHallucinationCount(1);
        } else {
            metric.setHallucinationCount(0);
        }
        return metric;
    }

    /**
     * RecordMetricRequest → HallucinationMetricEntity。
     */
    public HallucinationMetricEntity toEntity(RecordMetricRequest req) {
        HallucinationMetricEntity entity = new HallucinationMetricEntity();
        entity.setMetricId(UUID.randomUUID().toString());
        entity.setTenantId("");
        entity.setAgentId(req.getAgentId());
        entity.setStatDate(LocalDate.now());
        entity.setLayer(req.getLayer());
        entity.setEventType(req.getEventType());
        entity.setDetail(req.getDetail());
        entity.setTaskId(req.getTaskId());
        entity.setTotalClaims(1);
        if ("detected".equals(req.getEventType()) || "prevented".equals(req.getEventType())) {
            entity.setHallucinationCount(1);
        } else {
            entity.setHallucinationCount(0);
        }
        entity.setHallucinationRate(entity.getTotalClaims() == 0
                ? 0.0 : (double) entity.getHallucinationCount() / entity.getTotalClaims());
        return entity;
    }

    /**
     * 构造 RecordMetricAck。
     */
    public RecordMetricAck toRecordMetricAck(String metricId) {
        return RecordMetricAck.newBuilder()
                .setMetricId(metricId == null ? "" : metricId)
                .build();
    }

    // ===== 工具方法 =====

    /** 格式化 SelfCheckResult 为 proto 字符串（小写）。 */
    public String formatSelfCheckResult(SelfCheckResult result) {
        if (result == null) return "uncertain";
        return switch (result) {
            case PASS -> "verified";
            case SUSPECTED -> "hallucinated";
            case REFUSE -> "uncertain";
        };
    }

    /** 解析 proto result 字符串为 SelfCheckResult。 */
    public SelfCheckResult parseSelfCheckResult(String value) {
        if (value == null || value.isEmpty()) return SelfCheckResult.REFUSE;
        return switch (value.toLowerCase()) {
            case "verified", "pass" -> SelfCheckResult.PASS;
            case "hallucinated", "suspected" -> SelfCheckResult.SUSPECTED;
            default -> SelfCheckResult.REFUSE;
        };
    }

    /** 格式化 GuardResult 为 proto 字符串。 */
    public String formatGuardResult(GuardResult result) {
        if (result == null) return "block";
        return switch (result) {
            case ALLOWED -> "allow";
            case REJECTED -> "block";
        };
    }

    /** 解析 proto guard_result 字符串为 GuardResult。 */
    public GuardResult parseGuardResult(String value) {
        if (value == null || value.isEmpty()) return GuardResult.REJECTED;
        return switch (value.toLowerCase()) {
            case "allow", "allowed" -> GuardResult.ALLOWED;
            default -> GuardResult.REJECTED;
        };
    }

    /** 解析 proto layer 字符串为 HallucinationLayer。 */
    public HallucinationLayer parseLayer(String value) {
        if (value == null || value.isEmpty()) return HallucinationLayer.L6_METRIC;
        try {
            return switch (value.toLowerCase()) {
                case "self_check" -> HallucinationLayer.L2_SELF_CHECK;
                case "tool_guard" -> HallucinationLayer.L5_TOOL_GUARD;
                case "rag_anchor" -> HallucinationLayer.L3_RAG_ANCHOR;
                case "hard_validator" -> HallucinationLayer.L4_HARD;
                default -> HallucinationLayer.L6_METRIC;
            };
        } catch (Exception e) {
            return HallucinationLayer.L6_METRIC;
        }
    }

    /** 构造自检原因描述。 */
    private String buildSelfCheckReason(SelfCheckResult result, Claim claim) {
        return switch (result) {
            case PASS -> "Self-check passed: claim has source tag and no hallucination keywords";
            case SUSPECTED -> claim.isHasSourceTag()
                    ? "Self-check suspected: hallucination keywords detected"
                    : "Self-check suspected: missing source tag";
            case REFUSE -> "Self-check refuse: insufficient information";
        };
    }

    /** 简化锚定得分计算：基于源文档覆盖率。 */
    private double computeAnchorScore(String response, List<String> sourceDocs) {
        if (sourceDocs == null || sourceDocs.isEmpty()) return 0.0;
        // 简化: 每个源文档贡献 1/N 的锚定分
        return Math.min(1.0, sourceDocs.size() * 0.3);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
