package com.agent.quality.grpc;

import agentplatform.quality.v1.GetQualityMetricsRequest;
import agentplatform.quality.v1.GetQualityMetricsResponse;
import agentplatform.quality.v1.GetReviewQueueRequest;
import agentplatform.quality.v1.GetReviewQueueResponse;
import agentplatform.quality.v1.ReportBadcaseAck;
import agentplatform.quality.v1.ReportBadcaseRequest;
import agentplatform.quality.v1.ValidateTaskRequest;
import agentplatform.quality.v1.ValidateTaskResponse;
import com.agent.quality.api.BadcaseWriter;
import com.agent.quality.api.L4AuditValidator;
import com.agent.quality.api.L4ConsistencyValidator;
import com.agent.quality.api.L4HardValidator;
import com.agent.quality.api.ManualReviewQueue;
import com.agent.quality.config.QualityProperties;
import com.agent.quality.entity.BadcaseRecordEntity;
import com.agent.quality.entity.ReviewItemEntity;
import com.agent.quality.enums.BadcaseCategory;
import com.agent.quality.enums.BadcaseSeverity;
import com.agent.quality.model.L4ValidationOutput;
import com.agent.quality.model.ManualReviewItem;
import com.agent.quality.repository.BadcaseRecordRepository;
import com.agent.quality.repository.ReviewItemRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link QualityGrpcService} 单测（覆盖 4 RPC 正常流 + 异常流）。
 *
 * <p>纯单测：mock {@link L4HardValidator} / {@link L4ConsistencyValidator} /
 * {@link L4AuditValidator} / {@link BadcaseWriter} / {@link ManualReviewQueue} /
 * {@link BadcaseRecordRepository} / {@link ReviewItemRepository}，
 * 使用真实 {@link QualityMapper} + 真实 {@link GrpcExceptionAdvice} + 真实 {@link QualityProperties}，
 * 用 capturing StreamObserver 捕获 onNext/onError。</p>
 *
 * <p>验证场景：</p>
 * <ul>
 *   <li>ValidateTask 正常流 → onNext + onCompleted，验证 overallResult</li>
 *   <li>ValidateTask 空 taskId → onError INVALID_ARGUMENT</li>
 *   <li>ValidateTask 校验失败 → overallResult=fail</li>
 *   <li>ReportBadcase 正常流 → onNext + onCompleted，验证 badcaseId + needsReview</li>
 *   <li>ReportBadcase 高严重度 → needsReview=true</li>
 *   <li>ReportBadcase 空 taskId → onError INVALID_ARGUMENT</li>
 *   <li>GetReviewQueue 正常流 → 返回审核条目列表</li>
 *   <li>GetQualityMetrics 正常流 → 返回指标统计</li>
 * </ul>
 */
@DisplayName("QualityGrpcService gRPC 服务（4 RPC）")
class QualityGrpcServiceTest {

    private L4HardValidator hardValidator;
    private L4ConsistencyValidator consistencyValidator;
    private L4AuditValidator auditValidator;
    private BadcaseWriter badcaseWriter;
    private ManualReviewQueue manualReviewQueue;
    private BadcaseRecordRepository badcaseRecordRepository;
    private ReviewItemRepository reviewItemRepository;
    private QualityMapper mapper;
    private GrpcExceptionAdvice exceptionAdvice;
    private QualityProperties properties;
    private QualityGrpcService grpcService;

    @BeforeEach
    void setUp() {
        hardValidator = mock(L4HardValidator.class);
        consistencyValidator = mock(L4ConsistencyValidator.class);
        auditValidator = mock(L4AuditValidator.class);
        badcaseWriter = mock(BadcaseWriter.class);
        manualReviewQueue = mock(ManualReviewQueue.class);
        badcaseRecordRepository = mock(BadcaseRecordRepository.class);
        reviewItemRepository = mock(ReviewItemRepository.class);
        mapper = new QualityMapper();
        exceptionAdvice = new GrpcExceptionAdvice();
        properties = new QualityProperties();

        grpcService = new QualityGrpcService(
                hardValidator, consistencyValidator, auditValidator,
                badcaseWriter, manualReviewQueue,
                badcaseRecordRepository, reviewItemRepository,
                mapper, exceptionAdvice, properties);
    }

    // ===== RPC 1: ValidateTask =====

    @Test
    @DisplayName("Should_ValidateTask_When_TaskIdValid: 正常校验 → overallResult=pass")
    void should_ValidateTask_When_TaskIdValid() {
        // given
        L4ValidationOutput passOutput = new L4ValidationOutput(true, null);
        when(hardValidator.validate(anyString())).thenReturn(passOutput);
        when(consistencyValidator.validate(anyString(), anyString())).thenReturn(passOutput);
        when(auditValidator.validate(anyString(), any(double.class))).thenReturn(passOutput);

        ValidateTaskRequest req = ValidateTaskRequest.newBuilder()
                .setTaskId("task-001")
                .setAgentId("agent-001")
                .setResultJson("[来源:doc] test result")
                .addValidationLayers("hard")
                .build();

        // when
        CapturingObserver<ValidateTaskResponse> observer = new CapturingObserver<>();
        grpcService.validateTask(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        assertThat(observer.error).isNull();
        assertThat(observer.values).hasSize(1);
        ValidateTaskResponse resp = observer.values.get(0);
        assertThat(resp.getTaskId()).isEqualTo("task-001");
        assertThat(resp.getOverallResult()).isEqualTo("pass");
    }

    @Test
    @DisplayName("Should_ValidateTaskFail_When_HardValidationFails: 硬校验失败 → overallResult=fail")
    void should_ValidateTaskFail_When_HardValidationFails() {
        // given
        L4ValidationOutput failOutput = new L4ValidationOutput(false, null);
        failOutput.setViolationDetail("缺失来源标签 [来源:xxx]");
        when(hardValidator.validate(anyString())).thenReturn(failOutput);

        ValidateTaskRequest req = ValidateTaskRequest.newBuilder()
                .setTaskId("task-002")
                .setResultJson("no source tag")
                .addValidationLayers("hard")
                .build();

        // when
        CapturingObserver<ValidateTaskResponse> observer = new CapturingObserver<>();
        grpcService.validateTask(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        ValidateTaskResponse resp = observer.values.get(0);
        assertThat(resp.getOverallResult()).isEqualTo("fail");
        assertThat(resp.getIssuesList()).isNotEmpty();
    }

    @Test
    @DisplayName("Should_ThrowInvalidArgument_When_TaskIdEmpty: 空 taskId → onError INVALID_ARGUMENT")
    void should_ThrowInvalidArgument_When_TaskIdEmpty() {
        // given
        ValidateTaskRequest req = ValidateTaskRequest.newBuilder()
                .setTaskId("")
                .setResultJson("test")
                .build();

        // when
        CapturingObserver<ValidateTaskResponse> observer = new CapturingObserver<>();
        grpcService.validateTask(req, observer);

        // then
        assertThat(observer.completed).isFalse();
        assertThat(observer.error).isNotNull();
        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    // ===== RPC 2: ReportBadcase =====

    @Test
    @DisplayName("Should_ReportBadcase_When_TaskIdValid: 正常写入 → 返回 badcaseId")
    void should_ReportBadcase_When_TaskIdValid() {
        // given
        ReportBadcaseRequest req = ReportBadcaseRequest.newBuilder()
                .setTaskId("task-001")
                .setAgentId("agent-001")
                .setCategory("hallucination")
                .setSeverity("medium")
                .setDescription("hallucinated output")
                .build();

        // when
        CapturingObserver<ReportBadcaseAck> observer = new CapturingObserver<>();
        grpcService.reportBadcase(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        assertThat(observer.error).isNull();
        assertThat(observer.values).hasSize(1);
        ReportBadcaseAck ack = observer.values.get(0);
        assertThat(ack.getBadcaseId()).isNotEmpty();
        verify(badcaseWriter).write(any());
    }

    @Test
    @DisplayName("Should_ReportBadcaseNeedsReview_When_HighSeverity: 高严重度 → needsReview=true")
    void should_ReportBadcaseNeedsReview_When_HighSeverity() {
        // given
        ReportBadcaseRequest req = ReportBadcaseRequest.newBuilder()
                .setTaskId("task-002")
                .setAgentId("agent-001")
                .setCategory("hallucination")
                .setSeverity("high")
                .setDescription("severe hallucination")
                .build();

        // when
        CapturingObserver<ReportBadcaseAck> observer = new CapturingObserver<>();
        grpcService.reportBadcase(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        ReportBadcaseAck ack = observer.values.get(0);
        assertThat(ack.getNeedsReview()).isTrue();
        verify(manualReviewQueue).push(any(ManualReviewItem.class));
    }

    @Test
    @DisplayName("Should_ReportBadcaseNoReview_When_LowSeverity: 低严重度 → needsReview=false")
    void should_ReportBadcaseNoReview_When_LowSeverity() {
        // given
        ReportBadcaseRequest req = ReportBadcaseRequest.newBuilder()
                .setTaskId("task-003")
                .setAgentId("agent-001")
                .setCategory("format_error")
                .setSeverity("low")
                .setDescription("minor format issue")
                .build();

        // when
        CapturingObserver<ReportBadcaseAck> observer = new CapturingObserver<>();
        grpcService.reportBadcase(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        ReportBadcaseAck ack = observer.values.get(0);
        assertThat(ack.getNeedsReview()).isFalse();
        verify(manualReviewQueue, never()).push(any());
    }

    @Test
    @DisplayName("Should_ThrowInvalidArgument_When_ReportBadcaseTaskIdEmpty: 空 taskId → onError INVALID_ARGUMENT")
    void should_ThrowInvalidArgument_When_ReportBadcaseTaskIdEmpty() {
        // given
        ReportBadcaseRequest req = ReportBadcaseRequest.newBuilder()
                .setTaskId("")
                .setCategory("other")
                .setSeverity("low")
                .build();

        // when
        CapturingObserver<ReportBadcaseAck> observer = new CapturingObserver<>();
        grpcService.reportBadcase(req, observer);

        // then
        assertThat(observer.completed).isFalse();
        assertThat(observer.error).isNotNull();
        StatusRuntimeException sre = (StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        verify(badcaseWriter, never()).write(any());
    }

    // ===== RPC 3: GetReviewQueue =====

    @Test
    @DisplayName("Should_GetReviewQueue_When_StatusPending: 查询待审核条目 → 返回列表")
    void should_GetReviewQueue_When_StatusPending() {
        // given
        ReviewItemEntity entity = new ReviewItemEntity();
        entity.setReviewId("rev-001");
        entity.setBadcaseId("bc-001");
        entity.setSeverity(BadcaseSeverity.HIGH);
        entity.setStatus("pending");
        entity.setEnqueuedAt(Instant.now());
        when(reviewItemRepository.findByStatusOrderByEnqueuedAtAsc("pending"))
                .thenReturn(List.of(entity));

        GetReviewQueueRequest req = GetReviewQueueRequest.newBuilder()
                .setStatus("pending")
                .setPage(1)
                .setSize(20)
                .build();

        // when
        CapturingObserver<GetReviewQueueResponse> observer = new CapturingObserver<>();
        grpcService.getReviewQueue(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        GetReviewQueueResponse resp = observer.values.get(0);
        assertThat(resp.getItemsList()).hasSize(1);
        assertThat(resp.getItems(0).getReviewId()).isEqualTo("rev-001");
        assertThat(resp.getPagination().getTotal()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should_GetReviewQueueEmpty_When_NoItems: 队列为空 → 返回空列表")
    void should_GetReviewQueueEmpty_When_NoItems() {
        // given
        when(reviewItemRepository.findByStatusOrderByEnqueuedAtAsc("pending"))
                .thenReturn(Collections.emptyList());

        GetReviewQueueRequest req = GetReviewQueueRequest.newBuilder()
                .setStatus("pending")
                .setPage(1)
                .setSize(20)
                .build();

        // when
        CapturingObserver<GetReviewQueueResponse> observer = new CapturingObserver<>();
        grpcService.getReviewQueue(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        GetReviewQueueResponse resp = observer.values.get(0);
        assertThat(resp.getItemsList()).isEmpty();
        assertThat(resp.getPagination().getTotal()).isEqualTo(0);
    }

    // ===== RPC 4: GetQualityMetrics =====

    @Test
    @DisplayName("Should_GetQualityMetrics_When_RecordsExist: 返回质量指标统计")
    void should_GetQualityMetrics_When_RecordsExist() {
        // given
        BadcaseRecordEntity r1 = new BadcaseRecordEntity();
        r1.setBadcaseId("bc-001");
        r1.setTaskId("task-001");
        r1.setCategory(BadcaseCategory.HALLUCINATION);
        r1.setSeverity(BadcaseSeverity.HIGH);
        r1.setSeverityScore(0.9);
        r1.setContent("hallucinated");
        r1.setCreatedAt(Instant.now());

        BadcaseRecordEntity r2 = new BadcaseRecordEntity();
        r2.setBadcaseId("bc-002");
        r2.setTaskId("task-002");
        r2.setCategory(BadcaseCategory.FORMAT_ERROR);
        r2.setSeverity(BadcaseSeverity.LOW);
        r2.setSeverityScore(0.3);
        r2.setContent("format error");
        r2.setCreatedAt(Instant.now());

        when(badcaseRecordRepository.findAll()).thenReturn(List.of(r1, r2));

        GetQualityMetricsRequest req = GetQualityMetricsRequest.newBuilder().build();

        // when
        CapturingObserver<GetQualityMetricsResponse> observer = new CapturingObserver<>();
        grpcService.getQualityMetrics(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        GetQualityMetricsResponse resp = observer.values.get(0);
        assertThat(resp.getBadcaseCount()).isEqualTo(2);
        assertThat(resp.getFailCount()).isEqualTo(1); // HIGH
        assertThat(resp.getPassCount()).isEqualTo(1); // LOW
        assertThat(resp.getBreakdownList()).isNotEmpty();
    }

    @Test
    @DisplayName("Should_GetQualityMetricsEmpty_When_NoRecords: 无记录 → 零指标")
    void should_GetQualityMetricsEmpty_When_NoRecords() {
        // given
        when(badcaseRecordRepository.findAll()).thenReturn(Collections.emptyList());

        GetQualityMetricsRequest req = GetQualityMetricsRequest.newBuilder().build();

        // when
        CapturingObserver<GetQualityMetricsResponse> observer = new CapturingObserver<>();
        grpcService.getQualityMetrics(req, observer);

        // then
        assertThat(observer.completed).isTrue();
        GetQualityMetricsResponse resp = observer.values.get(0);
        assertThat(resp.getBadcaseCount()).isEqualTo(0);
        assertThat(resp.getTotalTasks()).isEqualTo(0);
        assertThat(resp.getPassRate()).isEqualTo(0.0);
    }

    // ===== 辅助方法 =====

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
