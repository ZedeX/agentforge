package com.agent.quality.grpc;

import agentplatform.quality.v1.ReportBadcaseRequest;
import agentplatform.quality.v1.ValidateTaskRequest;
import com.agent.quality.entity.BadcaseRecordEntity;
import com.agent.quality.entity.ReviewItemEntity;
import com.agent.quality.enums.BadcaseCategory;
import com.agent.quality.enums.BadcaseSeverity;
import com.agent.quality.enums.L4ValidationResult;
import com.agent.quality.model.BadcaseRecord;
import com.agent.quality.model.L4ValidationOutput;
import com.agent.quality.model.ManualReviewItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link QualityMapper} 单测（proto <-> POJO / Entity 双向转换）。
 */
@DisplayName("QualityMapper Proto/POJO/Entity 映射")
class QualityMapperTest {

    private QualityMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new QualityMapper();
    }

    // ===== proto request -> POJO =====

    @Test
    @DisplayName("ReportBadcaseRequest -> BadcaseRecord: category=hallucination severity=critical")
    void should_MapToBadcaseRecord_When_ReportRequestValid() {
        ReportBadcaseRequest req = ReportBadcaseRequest.newBuilder()
                .setTaskId("task-001")
                .setAgentId("agent-001")
                .setCategory("hallucination")
                .setSeverity("critical")
                .setDescription("幻觉描述")
                .setContextJson("{\"key\":\"value\"}")
                .build();

        BadcaseRecord record = mapper.toBadcaseRecord(req);

        assertThat(record.getTaskId()).isEqualTo("task-001");
        assertThat(record.getCategory()).isEqualTo(BadcaseCategory.HALLUCINATION);
        assertThat(record.getSeverity()).isEqualTo(BadcaseSeverity.HIGH);
        assertThat(record.getContent()).isEqualTo("幻觉描述");
        assertThat(record.getFailureReason()).isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    @DisplayName("ReportBadcaseRequest 未知 category -> OTHER")
    void should_MapToOther_When_CategoryUnknown() {
        ReportBadcaseRequest req = ReportBadcaseRequest.newBuilder()
                .setTaskId("task-002")
                .setCategory("unknown_type")
                .setSeverity("low")
                .setDescription("test")
                .build();

        BadcaseRecord record = mapper.toBadcaseRecord(req);
        assertThat(record.getCategory()).isEqualTo(BadcaseCategory.OTHER);
    }

    // ===== POJO -> Entity =====

    @Test
    @DisplayName("BadcaseRecord -> BadcaseRecordEntity: 字段完整映射")
    void should_MapToEntity_When_BadcaseRecordComplete() {
        BadcaseRecord record = new BadcaseRecord("bc-001", "task-001", BadcaseCategory.HALLUCINATION);
        record.setSeverity(BadcaseSeverity.HIGH);
        record.setContent("幻觉输出");
        record.setFailureReason("缺来源标签");
        record.setSeverityScore(0.9);
        record.setCreatedAt(Instant.now());

        BadcaseRecordEntity entity = mapper.toEntity(record);

        assertThat(entity.getBadcaseId()).isEqualTo("bc-001");
        assertThat(entity.getTaskId()).isEqualTo("task-001");
        assertThat(entity.getCategory()).isEqualTo(BadcaseCategory.HALLUCINATION);
        assertThat(entity.getSeverity()).isEqualTo(BadcaseSeverity.HIGH);
        assertThat(entity.getContent()).isEqualTo("幻觉输出");
        assertThat(entity.getFailureReason()).isEqualTo("缺来源标签");
        assertThat(entity.getSeverityScore()).isEqualTo(0.9);
    }

    @Test
    @DisplayName("BadcaseRecordEntity -> BadcaseRecord: 字段完整映射")
    void should_MapToDomain_When_EntityComplete() {
        BadcaseRecordEntity entity = new BadcaseRecordEntity();
        entity.setBadcaseId("bc-002");
        entity.setTaskId("task-002");
        entity.setCategory(BadcaseCategory.FORMAT_ERROR);
        entity.setSeverity(BadcaseSeverity.MEDIUM);
        entity.setContent("格式错误");
        entity.setFailureReason("JSON 不配对");
        entity.setSeverityScore(0.6);
        entity.setCreatedAt(Instant.now());

        BadcaseRecord record = mapper.toDomain(entity);

        assertThat(record.getBadcaseId()).isEqualTo("bc-002");
        assertThat(record.getTaskId()).isEqualTo("task-002");
        assertThat(record.getCategory()).isEqualTo(BadcaseCategory.FORMAT_ERROR);
        assertThat(record.getSeverity()).isEqualTo(BadcaseSeverity.MEDIUM);
        assertThat(record.getContent()).isEqualTo("格式错误");
    }

    // ===== ManualReviewItem <-> ReviewItemEntity =====

    @Test
    @DisplayName("ManualReviewItem -> ReviewItemEntity -> ManualReviewItem: 往返一致")
    void should_RoundTrip_When_ManualReviewItemMapping() {
        ManualReviewItem item = new ManualReviewItem("bc-003", BadcaseSeverity.HIGH);
        item.setReviewId("rv-001");
        item.setReviewer("reviewer-1");
        item.setReviewResult("confirmed");
        item.setEnqueuedAt(Instant.now());

        ReviewItemEntity entity = mapper.toEntity(item);
        assertThat(entity.getReviewId()).isEqualTo("rv-001");
        assertThat(entity.getBadcaseId()).isEqualTo("bc-003");
        assertThat(entity.getSeverity()).isEqualTo(BadcaseSeverity.HIGH);
        assertThat(entity.getReviewer()).isEqualTo("reviewer-1");

        ManualReviewItem back = mapper.toDomain(entity);
        assertThat(back.getReviewId()).isEqualTo("rv-001");
        assertThat(back.getBadcaseId()).isEqualTo("bc-003");
        assertThat(back.getSeverity()).isEqualTo(BadcaseSeverity.HIGH);
    }

    // ===== L4ValidationOutput -> ValidateTaskResponse =====

    @Test
    @DisplayName("L4 校验结果 -> ValidateTaskResponse: 全部通过 -> overall=pass")
    void should_MapToPassResponse_When_AllLayersPass() {
        L4ValidationOutput passOutput = new L4ValidationOutput(true, L4ValidationResult.PASS);
        List<String> issues = List.of();

        var response = mapper.toValidateTaskResponse("task-001", List.of(passOutput), issues);

        assertThat(response.getTaskId()).isEqualTo("task-001");
        assertThat(response.getOverallResult()).isEqualTo("pass");
        assertThat(response.getLayersList()).hasSize(1);
        assertThat(response.getIssuesList()).isEmpty();
    }

    @Test
    @DisplayName("L4 校验结果 -> ValidateTaskResponse: 有失败 -> overall=fail")
    void should_MapToFailResponse_When_AnyLayerFails() {
        L4ValidationOutput failOutput = new L4ValidationOutput(false, L4ValidationResult.FORMAT_VIOLATION);
        failOutput.setViolationDetail("缺少来源标签");

        var response = mapper.toValidateTaskResponse("task-002", List.of(failOutput), List.of("L4-1: 缺少来源标签"));

        assertThat(response.getOverallResult()).isEqualTo("fail");
        assertThat(response.getLayersList()).hasSize(1);
        assertThat(response.getLayers(0).getLayer()).isEqualTo("hard");
        assertThat(response.getIssuesList()).hasSize(1);
    }

    // ===== QualityMetrics =====

    @Test
    @DisplayName("GetQualityMetricsResponse 构造: category breakdown 计算正确")
    void should_CalculateBreakdown_When_MetricsMapped() {
        Map<String, Long> categoryCounts = Map.of(
                "HALLUCINATION", 5L,
                "FORMAT_ERROR", 3L
        );

        var response = mapper.toQualityMetricsResponse(10, 5, 0, 5, 8, 0.5, 0.85, categoryCounts);

        assertThat(response.getTotalTasks()).isEqualTo(10);
        assertThat(response.getBadcaseCount()).isEqualTo(8);
        assertThat(response.getPassRate()).isEqualTo(0.5);
        assertThat(response.getBreakdownList()).hasSize(2);
        assertThat(response.getBreakdown(0).getCount()).isIn(5L, 3L);
    }

    // ===== parseBadcaseCategory / parseBadcaseSeverity =====

    @Test
    @DisplayName("parseBadcaseCategory: 全部枚举值 + 无效值")
    void should_ParseAllCategories() {
        assertThat(mapper.parseBadcaseCategory("HALLUCINATION")).isEqualTo(BadcaseCategory.HALLUCINATION);
        assertThat(mapper.parseBadcaseCategory("FORMAT_ERROR")).isEqualTo(BadcaseCategory.FORMAT_ERROR);
        assertThat(mapper.parseBadcaseCategory("FACT_INCONSISTENCY")).isEqualTo(BadcaseCategory.FACT_INCONSISTENCY);
        assertThat(mapper.parseBadcaseCategory("AUDIT_REJECTED")).isEqualTo(BadcaseCategory.AUDIT_REJECTED);
        assertThat(mapper.parseBadcaseCategory("OTHER")).isEqualTo(BadcaseCategory.OTHER);
        assertThat(mapper.parseBadcaseCategory("invalid")).isEqualTo(BadcaseCategory.OTHER);
        assertThat(mapper.parseBadcaseCategory(null)).isEqualTo(BadcaseCategory.OTHER);
        assertThat(mapper.parseBadcaseCategory("")).isEqualTo(BadcaseCategory.OTHER);
    }

    @Test
    @DisplayName("parseBadcaseSeverity: 全部枚举值 + 无效值")
    void should_ParseAllSeverities() {
        assertThat(mapper.parseBadcaseSeverity("LOW")).isEqualTo(BadcaseSeverity.LOW);
        assertThat(mapper.parseBadcaseSeverity("MEDIUM")).isEqualTo(BadcaseSeverity.MEDIUM);
        assertThat(mapper.parseBadcaseSeverity("HIGH")).isEqualTo(BadcaseSeverity.HIGH);
        assertThat(mapper.parseBadcaseSeverity("invalid")).isEqualTo(BadcaseSeverity.LOW);
        assertThat(mapper.parseBadcaseSeverity(null)).isEqualTo(BadcaseSeverity.LOW);
    }
}
