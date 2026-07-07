package com.agent.observability.grpc;

import agentplatform.observability.v1.GetHealthRequest;
import agentplatform.observability.v1.GetHealthResponse;
import agentplatform.observability.v1.GetMetricsRequest;
import agentplatform.observability.v1.GetMetricsResponse;
import agentplatform.observability.v1.GetTracesRequest;
import agentplatform.observability.v1.GetTracesResponse;
import agentplatform.observability.v1.ObservabilityServiceGrpc;
import com.agent.observability.api.HealthAggregator;
import com.agent.observability.api.MetricsCollector;
import com.agent.observability.api.TraceQueryService;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * ObservabilityService gRPC server implementation (3 RPCs).
 *
 * <p>Implements {@code GetTraces} / {@code GetMetrics} / {@code GetHealth} by delegating
 * to {@link TraceQueryService} / {@link MetricsCollector} / {@link HealthAggregator}.</p>
 */
@Slf4j
@GrpcService
public class ObservabilityGrpcService extends ObservabilityServiceGrpc.ObservabilityServiceImplBase {

    private final TraceQueryService traceQueryService;
    private final MetricsCollector metricsCollector;
    private final HealthAggregator healthAggregator;
    private final ObservabilityMapper mapper;
    private final GrpcExceptionAdvice exceptionAdvice;

    public ObservabilityGrpcService(TraceQueryService traceQueryService,
                                    MetricsCollector metricsCollector,
                                    HealthAggregator healthAggregator,
                                    ObservabilityMapper mapper,
                                    GrpcExceptionAdvice exceptionAdvice) {
        this.traceQueryService = traceQueryService;
        this.metricsCollector = metricsCollector;
        this.healthAggregator = healthAggregator;
        this.mapper = mapper;
        this.exceptionAdvice = exceptionAdvice;
    }

    // ===== RPC 1: GetTraces =====

    @Override
    public void getTraces(GetTracesRequest request,
                          StreamObserver<GetTracesResponse> responseObserver) {
        try {
            com.agent.observability.model.GetTracesRequest pojoRequest = mapper.toTracesRequest(request);
            com.agent.observability.model.GetTracesResponse pojoResponse = traceQueryService.query(pojoRequest);
            GetTracesResponse response = mapper.toTracesResponse(pojoResponse);
            log.info("getTraces success traceId={} serviceName={} count={}",
                    request.getTraceId(), request.getServiceName(), pojoResponse.getTraces().size());
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Throwable t) {
            exceptionAdvice.translate(t, responseObserver);
        }
    }

    // ===== RPC 2: GetMetrics =====

    @Override
    public void getMetrics(GetMetricsRequest request,
                           StreamObserver<GetMetricsResponse> responseObserver) {
        try {
            com.agent.observability.model.GetMetricsRequest pojoRequest = mapper.toMetricsRequest(request);
            com.agent.observability.model.GetMetricsResponse pojoResponse = metricsCollector.collect(pojoRequest);
            GetMetricsResponse response = mapper.toMetricsResponse(pojoResponse);
            log.info("getMetrics success serviceName={} metricName={} count={}",
                    request.getServiceName(), request.getMetricName(), pojoResponse.getDataPoints().size());
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Throwable t) {
            exceptionAdvice.translate(t, responseObserver);
        }
    }

    // ===== RPC 3: GetHealth =====

    @Override
    public void getHealth(GetHealthRequest request,
                          StreamObserver<GetHealthResponse> responseObserver) {
        try {
            com.agent.observability.model.GetHealthRequest pojoRequest = mapper.toHealthRequest(request);
            com.agent.observability.model.GetHealthResponse pojoResponse = healthAggregator.aggregate(pojoRequest);
            GetHealthResponse response = mapper.toHealthResponse(pojoResponse);
            log.info("getHealth success services={} overallStatus={}",
                    pojoResponse.getServices().size(), pojoResponse.getOverallStatus());
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Throwable t) {
            exceptionAdvice.translate(t, responseObserver);
        }
    }
}
