package com.agent.quality.grpc;

import agentplatform.common.v1.Pagination;
import agentplatform.quality.v1.GetQualityMetricsRequest;
import agentplatform.quality.v1.GetQualityMetricsResponse;
import agentplatform.quality.v1.GetReviewQueueRequest;
import agentplatform.quality.v1.GetReviewQueueResponse;
import agentplatform.quality.v1.QualityServiceGrpc;
import agentplatform.quality.v1.ReportBadcaseAck;
import agentplatform.quality.v1.ReportBadcaseRequest;
import agentplatform.quality.v1.ReviewItem;
import agentplatform.quality.v1.ValidateTaskRequest;
import agentplatform.quality.v1.ValidateTaskResponse;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.quality.api.BadcaseWriter;
import com.agent.quality.api.L4AuditValidator;
import com.agent.quality.api.L4ConsistencyValidator;
import com.agent.quality.api.L4HardValidator;
import com.agent.quality.api.ManualReviewQueue;
import com.agent.quality.config.QualityProperties;
import com.agent.quality.entity.BadcaseRecordEntity;
import com.agent.quality.enums.BadcaseSeverity;
import com.agent.quality.model.BadcaseRecord;
import com.agent.quality.model.L4ValidationOutput;
import com.agent.quality.model.ManualReviewItem;
import com.agent.quality.repository.BadcaseRecordRepository;
import com.agent.quality.repository.ReviewItemRepository;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * QualityService gRPC 服务端实现（4 RPC）。
 *
 * <p>覆盖 {@link QualityServiceGrpc.QualityServiceImplBase} 的 4 个 RPC：
 * {@code validateTask} / {@code reportBadcase} / {@code getReviewQueue} / {@code getQualityMetrics}。</p>
 *
 * <p>职责：proto request → 调用业务服务 → {@link QualityMapper} 转 proto response → 下发 observer。
 * 异常通过 {@link GrpcExceptionAdvice} 统一翻译为 gRPC Status。</p>
 */
@Slf4j
@GrpcService
public class QualityGrpcService extends QualityServiceGrpc.QualityServiceImplBase {

    private final L4HardValidator hardValidator;
    private final L4ConsistencyValidator consistencyValidator;
    private final L4AuditValidator auditValidator;
    private final BadcaseWriter badcaseWriter;
    private final ManualReviewQueue manualReviewQueue;
    private final BadcaseRecordRepository badcaseRecordRepository;
    private final ReviewItemRepository reviewItemRepository;
    private final QualityMapper mapper;
    private final GrpcExceptionAdvice exceptionAdvice;
    private final QualityProperties properties;

    public QualityGrpcService(L4HardValidator hardValidator,
                               L4ConsistencyValidator consistencyValidator,
                               L4AuditValidator auditValidator,
                               BadcaseWriter badcaseWriter,
                               ManualReviewQueue manualReviewQueue,
                               BadcaseRecordRepository badcaseRecordRepository,
                               ReviewItemRepository reviewItemRepository,
                               QualityMapper mapper,
                               GrpcExceptionAdvice exceptionAdvice,
                               QualityProperties properties) {
        this.hardValidator = hardValidator;
        this.consistencyValidator = consistencyValidator;
        this.auditValidator = auditValidator;
        this.badcaseWriter = badcaseWriter;
        this.manualReviewQueue = manualReviewQueue;
        this.badcaseRecordRepository = badcaseRecordRepository;
        this.reviewItemRepository = reviewItemRepository;
        this.mapper = mapper;
        this.exceptionAdvice = exceptionAdvice;
        this.properties = properties;
    }

    // ===== RPC 1: ValidateTask =====

    @Override
    public void validateTask(ValidateTaskRequest request,
                              StreamObserver<ValidateTaskResponse> responseObserver) {
        try {
            if (request.getTaskId() == null || request.getTaskId().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "task_id must not be empty");
            }

            String resultJson = request.getResultJson();
            List<String> layers = request.getValidationLayersList().isEmpty()
                    ? properties.getValidation().getDefaultLayers()
                    : request.getValidationLayersList();

            List<L4ValidationOutput> outputs = new ArrayList<>();
            List<String> issues = new ArrayList<>();

            for (String layer : layers) {
                L4ValidationOutput output = switch (layer.toLowerCase()) {
                    case "hard" -> hardValidator.validate(resultJson);
                    case "consistency" -> consistencyValidator.validate(resultJson, "");
                    case "audit" -> auditValidator.validate(resultJson,
                            properties.getL4().getAuditThreshold());
                    default -> {
                        log.warn("Unknown validation layer: {}, skipping", layer);
                        yield null;
                    }
                };
                if (output != null) {
                    outputs.add(output);
                    if (!output.isPassed() && output.getViolationDetail() != null) {
                        issues.add(layer + ": " + output.getViolationDetail());
                    }
                }
            }

            // Determine overall result
            String overallResult = "pass";
            for (L4ValidationOutput output : outputs) {
                if (!output.isPassed()) {
                    overallResult = "fail";
                    break;
                }
            }

            ValidateTaskResponse response = mapper.toValidateTaskResponse(
                    request.getTaskId(), overallResult, outputs, issues);
            log.info("validateTask success taskId={} overallResult={} layers={}",
                    request.getTaskId(), overallResult, outputs.size());
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Throwable t) {
            exceptionAdvice.translate(t, responseObserver);
        }
    }

    // ===== RPC 2: ReportBadcase =====

    @Override
    public void reportBadcase(ReportBadcaseRequest request,
                               StreamObserver<ReportBadcaseAck> responseObserver) {
        try {
            if (request.getTaskId() == null || request.getTaskId().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "task_id must not be empty");
            }

            // Convert proto → domain model and write
            BadcaseRecord record = mapper.toBadcaseRecord(request);

            badcaseWriter.write(record);

            // Check if needs manual review (severity >= threshold)
            double threshold = properties.getReview().getSeverityThreshold();
            boolean needsReview = record.getSeverityScore() >= threshold;

            if (needsReview) {
                ManualReviewItem item = new ManualReviewItem(
                        record.getBadcaseId(), record.getSeverity());
                manualReviewQueue.push(item);
                log.info("Badcase pushed to review queue: badcaseId={}, severityScore={}",
                        record.getBadcaseId(), record.getSeverityScore());
            }

            ReportBadcaseAck ack = ReportBadcaseAck.newBuilder()
                    .setBadcaseId(record.getBadcaseId() == null ? "" : record.getBadcaseId())
                    .setNeedsReview(needsReview)
                    .build();
            log.info("reportBadcase success taskId={} badcaseId={} needsReview={}",
                    request.getTaskId(), record.getBadcaseId(), needsReview);
            responseObserver.onNext(ack);
            responseObserver.onCompleted();
        } catch (Throwable t) {
            exceptionAdvice.translate(t, responseObserver);
        }
    }

    // ===== RPC 3: GetReviewQueue =====

    @Override
    public void getReviewQueue(GetReviewQueueRequest request,
                                StreamObserver<GetReviewQueueResponse> responseObserver) {
        try {
            String status = request.getStatus().isEmpty() ? "pending" : request.getStatus();
            int page = request.getPage() > 0 ? request.getPage() : 1;
            int size = request.getSize() > 0 ? request.getSize() : properties.getReview().getDefaultPageSize();

            List<com.agent.quality.entity.ReviewItemEntity> items =
                    reviewItemRepository.findByStatusOrderByEnqueuedAtAsc(status);

            // Manual pagination
            int total = items.size();
            int fromIndex = Math.min((page - 1) * size, total);
            int toIndex = Math.min(fromIndex + size, total);
            List<com.agent.quality.entity.ReviewItemEntity> pageItems = items.subList(fromIndex, toIndex);

            List<ReviewItem> protoItems = pageItems.stream()
                    .map(mapper::toProto)
                    .collect(Collectors.toList());

            GetReviewQueueResponse response = GetReviewQueueResponse.newBuilder()
                    .addAllItems(protoItems)
                    .setPagination(Pagination.newBuilder()
                            .setPage(page)
                            .setSize(size)
                            .setTotal(total)
                            .build())
                    .build();
            log.info("getReviewQueue success status={} page={} size={} total={}",
                    status, page, size, total);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Throwable t) {
            exceptionAdvice.translate(t, responseObserver);
        }
    }

    // ===== RPC 4: GetQualityMetrics =====

    @Override
    public void getQualityMetrics(GetQualityMetricsRequest request,
                                   StreamObserver<GetQualityMetricsResponse> responseObserver) {
        try {
            // Aggregate metrics from badcase records
            List<BadcaseRecordEntity> allRecords = badcaseRecordRepository.findAll();

            long badcaseCount = allRecords.size();
            Map<String, Long> categoryCounts = new HashMap<>();
            for (BadcaseRecordEntity record : allRecords) {
                String cat = record.getCategory() == null ? "OTHER" : record.getCategory().name();
                categoryCounts.merge(cat, 1L, Long::sum);
            }

            // Skeleton stage: derive pass/warn/fail from severity distribution
            long highCount = allRecords.stream()
                    .filter(r -> r.getSeverity() == BadcaseSeverity.HIGH).count();
            long mediumCount = allRecords.stream()
                    .filter(r -> r.getSeverity() == BadcaseSeverity.MEDIUM).count();
            long lowCount = allRecords.stream()
                    .filter(r -> r.getSeverity() == BadcaseSeverity.LOW).count();

            long failCount = highCount;
            long warnCount = mediumCount;
            long passCount = lowCount;
            long totalTasks = failCount + warnCount + passCount;

            GetQualityMetricsResponse response = mapper.toQualityMetricsResponse(
                    totalTasks, passCount, warnCount, failCount, badcaseCount, categoryCounts);
            log.info("getQualityMetrics success totalTasks={} badcaseCount={}", totalTasks, badcaseCount);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Throwable t) {
            exceptionAdvice.translate(t, responseObserver);
        }
    }

    // ===== helpers =====

    private double inferSeverityScore(BadcaseSeverity severity) {
        if (severity == null) {
            return 0.5;
        }
        return switch (severity) {
            case HIGH -> 0.9;
            case MEDIUM -> 0.6;
            case LOW -> 0.3;
        };
    }
}
