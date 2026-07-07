package com.agent.hallucination.grpc;

import agentplatform.hallucination.v1.AnchorRagRequest;
import agentplatform.hallucination.v1.AnchorRagResponse;
import agentplatform.hallucination.v1.GuardToolCallRequest;
import agentplatform.hallucination.v1.GuardToolCallResponse;
import agentplatform.hallucination.v1.HallucinationGovernanceServiceGrpc;
import agentplatform.hallucination.v1.RecordMetricAck;
import agentplatform.hallucination.v1.RecordMetricRequest;
import agentplatform.hallucination.v1.SelfCheckRequest;
import agentplatform.hallucination.v1.SelfCheckResponse;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
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
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;

/**
 * HallucinationGovernanceService gRPC 服务端实现（F10 六层幻觉治理，4 RPC）。
 *
 * <p>覆盖 {@link HallucinationGovernanceServiceGrpc.HallucinationGovernanceServiceImplBase} 的 4 个 RPC：
 * {@code selfCheck} / {@code guardToolCall} / {@code anchorRag} / {@code recordMetric}。</p>
 *
 * <p>职责：proto request → mapper 转 domain model → 调用业务服务 → mapper 转 proto response → 下发 observer。
 * 异常通过 {@link GrpcExceptionAdvice} 统一翻译为 gRPC Status。</p>
 */
@Slf4j
@GrpcService
public class HallucinationGrpcService extends HallucinationGovernanceServiceGrpc.HallucinationGovernanceServiceImplBase {

    private final SelfCheckEngine selfCheckEngine;
    private final ToolGatewayGuard toolGatewayGuard;
    private final RagAnchor ragAnchor;
    private final HallucinationMetricWriter metricWriter;
    private final HallucinationMetricRepository metricRepository;
    private final HallucinationMapper mapper;
    private final GrpcExceptionAdvice exceptionAdvice;

    public HallucinationGrpcService(SelfCheckEngine selfCheckEngine,
                                     ToolGatewayGuard toolGatewayGuard,
                                     RagAnchor ragAnchor,
                                     HallucinationMetricWriter metricWriter,
                                     HallucinationMetricRepository metricRepository,
                                     HallucinationMapper mapper,
                                     GrpcExceptionAdvice exceptionAdvice) {
        this.selfCheckEngine = selfCheckEngine;
        this.toolGatewayGuard = toolGatewayGuard;
        this.ragAnchor = ragAnchor;
        this.metricWriter = metricWriter;
        this.metricRepository = metricRepository;
        this.mapper = mapper;
        this.exceptionAdvice = exceptionAdvice;
    }

    // ===== RPC 1: SelfCheck =====

    @Override
    public void selfCheck(SelfCheckRequest request,
                          StreamObserver<SelfCheckResponse> responseObserver) {
        try {
            if (request.getClaim() == null || request.getClaim().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "claim must not be empty");
            }
            Claim claim = mapper.toClaim(request);
            SelfCheckResult result = selfCheckEngine.check(claim);
            SelfCheckResponse response = mapper.toSelfCheckResponse(result, claim);
            log.info("selfCheck success task_id={} result={}", request.getTaskId(), result);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }

    // ===== RPC 2: GuardToolCall =====

    @Override
    public void guardToolCall(GuardToolCallRequest request,
                              StreamObserver<GuardToolCallResponse> responseObserver) {
        try {
            if (request.getToolId() == null || request.getToolId().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "tool_id must not be empty");
            }
            ToolCallGuardRequest guardReq = mapper.toToolCallGuardRequest(request);
            GuardResult guardResult = toolGatewayGuard.guard(guardReq);
            GuardToolCallResponse response = mapper.toGuardToolCallResponse(guardResult, request.getToolId());
            log.info("guardToolCall success task_id={} tool_id={} result={}",
                    request.getTaskId(), request.getToolId(), guardResult);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }

    // ===== RPC 3: AnchorRag =====

    @Override
    public void anchorRag(AnchorRagRequest request,
                          StreamObserver<AnchorRagResponse> responseObserver) {
        try {
            if (request.getResponse() == null || request.getResponse().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "response must not be empty");
            }
            String factualTask = mapper.toFactualTask(request);
            boolean anchored = ragAnchor.anchor(factualTask);
            AnchorRagResponse response = mapper.toAnchorRagResponse(
                    anchored, request.getResponse(), request.getSourceDocsList());
            log.info("anchorRag success task_id={} anchored={}", request.getTaskId(), anchored);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }

    // ===== RPC 4: RecordMetric =====

    @Override
    public void recordMetric(RecordMetricRequest request,
                             StreamObserver<RecordMetricAck> responseObserver) {
        try {
            if (request.getAgentId() == null || request.getAgentId().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "agent_id must not be empty");
            }
            if (request.getLayer() == null || request.getLayer().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "layer must not be empty");
            }
            // 1. 写入内存聚合（HallucinationMetricWriterImpl）
            HallucinationMetric metric = mapper.toMetric(request);
            metricWriter.write(metric);

            // 2. 持久化到 JPA
            HallucinationMetricEntity entity = mapper.toEntity(request);
            HallucinationMetricEntity saved = metricRepository.save(entity);

            RecordMetricAck ack = mapper.toRecordMetricAck(saved.getMetricId());
            log.info("recordMetric success task_id={} agent_id={} layer={} metric_id={}",
                    request.getTaskId(), request.getAgentId(), request.getLayer(), saved.getMetricId());
            responseObserver.onNext(ack);
            responseObserver.onCompleted();
        } catch (Exception e) {
            exceptionAdvice.translate(e, responseObserver);
        }
    }
}
