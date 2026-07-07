package com.agent.observability.grpc;

import agentplatform.observability.v1.GetHealthRequest;
import agentplatform.observability.v1.GetHealthResponse;
import agentplatform.observability.v1.GetMetricsRequest;
import agentplatform.observability.v1.GetMetricsResponse;
import agentplatform.observability.v1.GetTracesRequest;
import agentplatform.observability.v1.GetTracesResponse;
import agentplatform.observability.v1.MetricDataPoint;
import agentplatform.observability.v1.ServiceHealth;
import agentplatform.observability.v1.TraceSummary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Proto ↔ POJO mapper for observability gRPC service.
 *
 * <p>Proto classes are imported; POJO model classes use fully-qualified names to avoid conflicts.</p>
 */
@Component
public class ObservabilityMapper {

    // ===== GetTraces =====

    /**
     * Convert proto GetTracesRequest to POJO GetTracesRequest.
     */
    public com.agent.observability.model.GetTracesRequest toTracesRequest(GetTracesRequest proto) {
        return new com.agent.observability.model.GetTracesRequest(
                proto.getTraceId(),
                proto.getServiceName(),
                proto.getStartTime(),
                proto.getEndTime(),
                proto.getLimit()
        );
    }

    /**
     * Convert POJO GetTracesResponse to proto GetTracesResponse.
     */
    public GetTracesResponse toTracesResponse(com.agent.observability.model.GetTracesResponse pojo) {
        List<TraceSummary> traces = pojo.getTraces().stream()
                .map(this::toProtoTraceSummary)
                .collect(Collectors.toList());
        return GetTracesResponse.newBuilder()
                .addAllTraces(traces)
                .build();
    }

    private TraceSummary toProtoTraceSummary(com.agent.observability.model.TraceSummary model) {
        return TraceSummary.newBuilder()
                .setTraceId(nullToEmpty(model.getTraceId()))
                .setRootService(nullToEmpty(model.getRootService()))
                .setStartTime(model.getStartTime())
                .setDurationMs(model.getDurationMs())
                .setSpanCount(model.getSpanCount())
                .setStatus(nullToEmpty(model.getStatus()))
                .build();
    }

    // ===== GetMetrics =====

    /**
     * Convert proto GetMetricsRequest to POJO GetMetricsRequest.
     */
    public com.agent.observability.model.GetMetricsRequest toMetricsRequest(GetMetricsRequest proto) {
        return new com.agent.observability.model.GetMetricsRequest(
                proto.getServiceName(),
                proto.getMetricName(),
                proto.getStartTime(),
                proto.getEndTime(),
                proto.getGranularity()
        );
    }

    /**
     * Convert POJO GetMetricsResponse to proto GetMetricsResponse.
     */
    public GetMetricsResponse toMetricsResponse(com.agent.observability.model.GetMetricsResponse pojo) {
        List<MetricDataPoint> dataPoints = pojo.getDataPoints().stream()
                .map(this::toProtoMetricDataPoint)
                .collect(Collectors.toList());
        return GetMetricsResponse.newBuilder()
                .addAllDataPoints(dataPoints)
                .build();
    }

    private MetricDataPoint toProtoMetricDataPoint(com.agent.observability.model.MetricDataPoint model) {
        MetricDataPoint.Builder builder = MetricDataPoint.newBuilder()
                .setMetricName(nullToEmpty(model.getMetricName()))
                .setServiceName(nullToEmpty(model.getServiceName()))
                .setTimestamp(model.getTimestamp())
                .setValue(model.getValue());
        if (model.getLabels() != null) {
            builder.putAllLabels(model.getLabels());
        }
        return builder.build();
    }

    // ===== GetHealth =====

    /**
     * Convert proto GetHealthRequest to POJO GetHealthRequest.
     */
    public com.agent.observability.model.GetHealthRequest toHealthRequest(GetHealthRequest proto) {
        return new com.agent.observability.model.GetHealthRequest(
                proto.getServiceNamesList()
        );
    }

    /**
     * Convert POJO GetHealthResponse to proto GetHealthResponse.
     */
    public GetHealthResponse toHealthResponse(com.agent.observability.model.GetHealthResponse pojo) {
        List<ServiceHealth> services = pojo.getServices().stream()
                .map(this::toProtoServiceHealth)
                .collect(Collectors.toList());
        return GetHealthResponse.newBuilder()
                .setOverallStatus(nullToEmpty(pojo.getOverallStatus()))
                .addAllServices(services)
                .build();
    }

    private ServiceHealth toProtoServiceHealth(com.agent.observability.model.ServiceHealth model) {
        return ServiceHealth.newBuilder()
                .setServiceName(nullToEmpty(model.getServiceName()))
                .setStatus(nullToEmpty(model.getStatus()))
                .setUptimeSeconds(model.getUptimeSeconds())
                .setErrorRate(model.getErrorRate())
                .setLatencyP95Ms(model.getLatencyP95Ms())
                .setDetail(nullToEmpty(model.getDetail()))
                .build();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
