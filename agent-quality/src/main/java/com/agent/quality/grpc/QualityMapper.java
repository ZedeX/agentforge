package com.agent.quality.grpc;

import agentplatform.quality.v1.CategoryBreakdown;
import agentplatform.quality.v1.GetQualityMetricsResponse;
import agentplatform.quality.v1.LayerValidationResult;
import agentplatform.quality.v1.ReportBadcaseRequest;
import agentplatform.quality.v1.ReviewItem;
import agentplatform.quality.v1.ValidateTaskResponse;
import com.agent.quality.entity.BadcaseRecordEntity;
import com.agent.quality.entity.ReviewItemEntity;
import com.agent.quality.enums.BadcaseCategory;
import com.agent.quality.enums.BadcaseSeverity;
import com.agent.quality.model.BadcaseRecord;
import com.agent.quality.model.L4ValidationOutput;
import com.agent.quality.model.ManualReviewItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Proto ↔ POJO / JPA Entity 双向映射器（对齐 agent-memory MemoryRecordMapper 模式）。
 *
 * <p>负责 gRPC proto 消息、业务 POJO 与 JPA Entity 三层之间的转换。</p>
 *
 * <p>字段映射约定：</p>
 * <ul>
 *   <li>{@code category}（proto string）↔ {@link BadcaseCategory} 枚举</li>
 *   <li>{@code severity}（proto string）↔ {@link BadcaseSeverity} 枚举</li>
 *   <li>时间戳：entity {@link java.time.Instant} ↔ proto int64 epoch millis</li>
 * </ul>
 */
@Component
public class QualityMapper {

    // ===== proto request -> POJO =====

    /**
     * ReportBadcaseRequest → BadcaseRecord (POJO)。
     *
     * <p>将 gRPC 上报 badcase 请求转换为业务 POJO，供 {@link com.agent.quality.api.BadcaseWriter} 使用。
     * severity 字符串 "critical" 作为 HIGH 的别名（对齐上游约定）。</p>
     */
    public BadcaseRecord toBadcaseRecord(ReportBadcaseRequest req) {
        BadcaseRecord record = new BadcaseRecord();
        record.setBadcaseId(generateBadcaseId());
        record.setTaskId(req.getTaskId());
        record.setCategory(parseBadcaseCategory(req.getCategory()));
        record.setSeverity(parseBadcaseSeverity(req.getSeverity()));
        record.setContent(req.getDescription());
        record.setFailureReason(req.getContextJson());
        record.setSeverityScore(inferSeverityScore(record.getSeverity()));
        return record;
    }

    // ===== entity -> proto =====

    /**
     * ReviewItemEntity → proto ReviewItem。
     */
    public ReviewItem toProto(ReviewItemEntity entity) {
        ReviewItem.Builder b = ReviewItem.newBuilder()
                .setReviewId(nullToEmpty(entity.getReviewId()))
                .setBadcaseId(nullToEmpty(entity.getBadcaseId()))
                .setSeverity(nullToEmpty(entity.getSeverity() == null ? null : entity.getSeverity().name()))
                .setStatus(nullToEmpty(entity.getStatus()))
                .setAssignedReviewer(nullToEmpty(entity.getReviewer()));
        if (entity.getEnqueuedAt() != null) {
            b.setCreatedAt(entity.getEnqueuedAt().toEpochMilli());
        }
        return b.build();
    }

    /**
     * BadcaseRecordEntity → proto ReviewItem（用于构建审核条目时填充 badcase 详情）。
     */
    public ReviewItem toProto(BadcaseRecordEntity entity) {
        ReviewItem.Builder b = ReviewItem.newBuilder()
                .setBadcaseId(nullToEmpty(entity.getBadcaseId()))
                .setTaskId(nullToEmpty(entity.getTaskId()))
                .setCategory(nullToEmpty(entity.getCategory() == null ? null : entity.getCategory().name()))
                .setSeverity(nullToEmpty(entity.getSeverity() == null ? null : entity.getSeverity().name()))
                .setDescription(nullToEmpty(entity.getContent()));
        if (entity.getCreatedAt() != null) {
            b.setCreatedAt(entity.getCreatedAt().toEpochMilli());
        }
        return b.build();
    }

    // ===== POJO -> entity =====

    /**
     * BadcaseRecord (POJO) → BadcaseRecordEntity。
     *
     * <p>供 {@link com.agent.quality.api.impl.JpaBadcaseWriterImpl} 使用：业务层通过 POJO 写入 badcase 时，
     * 转换为 JPA entity 持久化。保留 POJO 中已有的 badcaseId / severityScore / createdAt。</p>
     */
    public BadcaseRecordEntity toEntity(BadcaseRecord record) {
        BadcaseRecordEntity entity = new BadcaseRecordEntity();
        entity.setBadcaseId(record.getBadcaseId() == null || record.getBadcaseId().isBlank()
                ? generateBadcaseId() : record.getBadcaseId());
        entity.setTaskId(record.getTaskId());
        entity.setCategory(record.getCategory());
        entity.setSeverity(record.getSeverity());
        entity.setContent(record.getContent());
        entity.setFailureReason(record.getFailureReason());
        entity.setSeverityScore(record.getSeverityScore() > 0
                ? record.getSeverityScore()
                : inferSeverityScore(record.getSeverity()));
        if (record.getCreatedAt() != null) {
            entity.setCreatedAt(record.getCreatedAt());
        }
        return entity;
    }

    /**
     * ReportBadcaseRequest → BadcaseRecordEntity（proto 直转 entity，供不经过 POJO 的路径使用）。
     */
    public BadcaseRecordEntity toEntity(ReportBadcaseRequest req) {
        BadcaseRecordEntity entity = new BadcaseRecordEntity();
        entity.setBadcaseId(generateBadcaseId());
        entity.setTaskId(req.getTaskId());
        entity.setCategory(parseBadcaseCategory(req.getCategory()));
        entity.setSeverity(parseBadcaseSeverity(req.getSeverity()));
        entity.setContent(req.getDescription());
        entity.setSeverityScore(inferSeverityScore(entity.getSeverity()));
        return entity;
    }

    /**
     * ManualReviewItem (POJO) → ReviewItemEntity。
     */
    public ReviewItemEntity toEntity(ManualReviewItem item) {
        ReviewItemEntity entity = new ReviewItemEntity();
        entity.setReviewId(item.getReviewId());
        entity.setBadcaseId(item.getBadcaseId());
        entity.setSeverity(item.getSeverity());
        entity.setReviewer(item.getReviewer());
        entity.setReviewResult(item.getReviewResult());
        if (item.getEnqueuedAt() != null) {
            entity.setEnqueuedAt(item.getEnqueuedAt());
        }
        return entity;
    }

    // ===== entity -> POJO =====

    /**
     * BadcaseRecordEntity → BadcaseRecord (POJO)。
     */
    public BadcaseRecord toDomain(BadcaseRecordEntity entity) {
        BadcaseRecord record = new BadcaseRecord();
        record.setBadcaseId(entity.getBadcaseId());
        record.setTaskId(entity.getTaskId());
        record.setCategory(entity.getCategory());
        record.setSeverity(entity.getSeverity());
        record.setContent(entity.getContent());
        record.setFailureReason(entity.getFailureReason());
        record.setSeverityScore(entity.getSeverityScore());
        if (entity.getCreatedAt() != null) {
            record.setCreatedAt(entity.getCreatedAt());
        }
        return record;
    }

    /**
     * ReviewItemEntity → ManualReviewItem (POJO)。
     */
    public ManualReviewItem toDomain(ReviewItemEntity entity) {
        ManualReviewItem item = new ManualReviewItem();
        item.setReviewId(entity.getReviewId());
        item.setBadcaseId(entity.getBadcaseId());
        item.setSeverity(entity.getSeverity());
        item.setReviewer(entity.getReviewer());
        item.setReviewResult(entity.getReviewResult());
        if (entity.getEnqueuedAt() != null) {
            item.setEnqueuedAt(entity.getEnqueuedAt());
        }
        return item;
    }

    // ===== L4ValidationOutput -> proto helpers =====

    /**
     * 构建 ValidateTaskResponse（从各层校验结果汇总，自动推断 overallResult）。
     *
     * @param taskId  任务 ID
     * @param outputs 各层校验输出
     * @param issues  问题列表
     * @return proto 响应，overallResult = 全部 pass → "pass"，任一 fail → "fail"
     */
    public ValidateTaskResponse toValidateTaskResponse(String taskId,
                                                        List<L4ValidationOutput> outputs,
                                                        List<String> issues) {
        String overallResult = "pass";
        for (L4ValidationOutput output : outputs) {
            if (!output.isPassed()) {
                overallResult = "fail";
                break;
            }
        }
        return toValidateTaskResponse(taskId, overallResult, outputs, issues);
    }

    /**
     * 构建 ValidateTaskResponse（显式指定 overallResult）。
     */
    public ValidateTaskResponse toValidateTaskResponse(String taskId,
                                                        String overallResult,
                                                        List<L4ValidationOutput> outputs,
                                                        List<String> issues) {
        ValidateTaskResponse.Builder b = ValidateTaskResponse.newBuilder()
                .setTaskId(nullToEmpty(taskId))
                .setOverallResult(nullToEmpty(overallResult))
                .addAllIssues(issues);
        for (L4ValidationOutput output : outputs) {
            b.addLayers(LayerValidationResult.newBuilder()
                    .setLayer(mapLayerName(output.getResult()))
                    .setResult(output.isPassed() ? "pass" : "fail")
                    .setDetail(nullToEmpty(output.getViolationDetail()))
                    .setScore(output.getOverallScore() > 0 ? output.getOverallScore() : output.getCosineSim())
                    .build());
        }
        return b.build();
    }

    /**
     * 将 L4ValidationResult 映射为层级名称（对齐 F9 决策节点 D2/D3/D4）。
     *
     * <ul>
     *   <li>PASS → "pass"</li>
     *   <li>FORMAT_VIOLATION (F9.D2) → "hard"</li>
     *   <li>FACT_INCONSISTENCY (F9.D3) → "fact"</li>
     *   <li>AUDIT_REJECTED (F9.D4) → "audit"</li>
     * </ul>
     */
    private String mapLayerName(com.agent.quality.enums.L4ValidationResult result) {
        if (result == null) {
            return "";
        }
        return switch (result) {
            case PASS -> "pass";
            case FORMAT_VIOLATION -> "hard";
            case FACT_INCONSISTENCY -> "fact";
            case AUDIT_REJECTED -> "audit";
        };
    }

    /**
     * 构建 GetQualityMetricsResponse（8 参数完整版，含 passRate + avgScore）。
     */
    public GetQualityMetricsResponse toQualityMetricsResponse(
            int totalTasks, int passCount, int warnCount, int failCount,
            int badcaseCount, double passRate, double avgScore,
            Map<String, Long> categoryCounts) {
        GetQualityMetricsResponse.Builder b = GetQualityMetricsResponse.newBuilder()
                .setTotalTasks(totalTasks)
                .setPassCount(passCount)
                .setWarnCount(warnCount)
                .setFailCount(failCount)
                .setBadcaseCount(badcaseCount)
                .setPassRate(passRate)
                .setAvgScore(avgScore);
        for (Map.Entry<String, Long> entry : categoryCounts.entrySet()) {
            double percentage = badcaseCount > 0 ? (double) entry.getValue() / badcaseCount : 0.0;
            b.addBreakdown(CategoryBreakdown.newBuilder()
                    .setCategory(entry.getKey())
                    .setCount(entry.getValue())
                    .setPercentage(percentage)
                    .build());
        }
        return b.build();
    }

    /**
     * 构建 GetQualityMetricsResponse（6 参数简化版，自动计算 passRate，avgScore 默认 0）。
     */
    public GetQualityMetricsResponse toQualityMetricsResponse(
            long totalTasks, long passCount, long warnCount, long failCount,
            long badcaseCount, Map<String, Long> categoryCounts) {
        double passRate = totalTasks > 0 ? (double) passCount / totalTasks : 0.0;
        return toQualityMetricsResponse(
                (int) totalTasks, (int) passCount, (int) warnCount, (int) failCount,
                (int) badcaseCount, passRate, 0.0, categoryCounts);
    }

    // ===== 枚举解析 =====

    /** 解析 category 字符串为枚举（大小写不敏感，无效值默认 OTHER）。 */
    public BadcaseCategory parseBadcaseCategory(String value) {
        if (value == null || value.isEmpty()) {
            return BadcaseCategory.OTHER;
        }
        try {
            return BadcaseCategory.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BadcaseCategory.OTHER;
        }
    }

    /**
     * 解析 severity 字符串为枚举（大小写不敏感）。
     *
     * <p>别名映射：{@code "critical"} → {@link BadcaseSeverity#HIGH}。
     * 无效/null/空值默认 {@link BadcaseSeverity#LOW}。</p>
     */
    public BadcaseSeverity parseBadcaseSeverity(String value) {
        if (value == null || value.isEmpty()) {
            return BadcaseSeverity.LOW;
        }
        if ("critical".equalsIgnoreCase(value)) {
            return BadcaseSeverity.HIGH;
        }
        try {
            return BadcaseSeverity.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BadcaseSeverity.LOW;
        }
    }

    // ===== 工具方法 =====

    /** 根据严重度推断 severityScore。 */
    public double inferSeverityScore(BadcaseSeverity severity) {
        if (severity == null) {
            return 0.5;
        }
        return switch (severity) {
            case HIGH -> 0.9;
            case MEDIUM -> 0.6;
            case LOW -> 0.3;
        };
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String generateBadcaseId() {
        return "bc-" + System.nanoTime();
    }
}
