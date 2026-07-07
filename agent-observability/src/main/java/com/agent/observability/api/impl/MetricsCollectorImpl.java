package com.agent.observability.api.impl;

import com.agent.observability.api.MetricsCollector;
import com.agent.observability.config.ObservabilityProperties;
import com.agent.observability.entity.MetricDataPointEntity;
import com.agent.observability.model.GetMetricsRequest;
import com.agent.observability.model.GetMetricsResponse;
import com.agent.observability.model.MetricDataPoint;
import com.agent.observability.repository.MetricDataPointRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Metrics collector implementation.
 *
 * <p>Queries metric data from JPA repository.
 */
@Slf4j
@Component
public class MetricsCollectorImpl implements MetricsCollector {

    private final MetricDataPointRepository repository;
    private final ObservabilityProperties properties;

    public MetricsCollectorImpl(MetricDataPointRepository repository,
                                ObservabilityProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public GetMetricsResponse collect(GetMetricsRequest request) {
        String granularity = request.getGranularity() != null && !request.getGranularity().isEmpty()
                ? request.getGranularity()
                : properties.getMetrics().getGranularity();

        Instant start = Instant.ofEpochMilli(request.getStartTime());
        Instant end = Instant.ofEpochMilli(request.getEndTime());

        List<MetricDataPointEntity> entities;

        if (request.getServiceName() != null && !request.getServiceName().isEmpty()
                && request.getMetricName() != null && !request.getMetricName().isEmpty()) {
            entities = repository.findByServiceNameAndMetricNameAndTimestampBetween(
                    request.getServiceName(), request.getMetricName(), start, end);
        } else if (request.getServiceName() != null && !request.getServiceName().isEmpty()) {
            entities = repository.findByServiceNameAndTimestampBetween(
                    request.getServiceName(), start, end);
        } else {
            entities = repository.findByTimestampBetween(start, end);
        }

        List<MetricDataPoint> dataPoints = entities.stream()
                .map(this::toModel)
                .collect(Collectors.toList());

        log.info("Metrics collected: serviceName={} metricName={} granularity={} count={}",
                request.getServiceName(), request.getMetricName(), granularity, dataPoints.size());

        return new GetMetricsResponse(dataPoints);
    }

    private MetricDataPoint toModel(MetricDataPointEntity entity) {
        return new MetricDataPoint(
                entity.getMetricName(),
                entity.getServiceName(),
                entity.getTimestamp() != null ? entity.getTimestamp().toEpochMilli() : 0L,
                entity.getValue(),
                parseLabels(entity.getLabelsJson())
        );
    }

    private java.util.Map<String, String> parseLabels(String labelsJson) {
        java.util.Map<String, String> labels = new java.util.HashMap<>();
        if (labelsJson == null || labelsJson.isEmpty() || "{}".equals(labelsJson.trim())) {
            return labels;
        }
        // Simplified JSON parsing: {"key1":"value1","key2":"value2"}
        String inner = labelsJson.trim();
        if (inner.startsWith("{")) inner = inner.substring(1);
        if (inner.endsWith("}")) inner = inner.substring(0, inner.length() - 1);
        if (inner.isEmpty()) return labels;
        for (String pair : inner.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                labels.put(kv[0].trim().replace("\"", ""), kv[1].trim().replace("\"", ""));
            }
        }
        return labels;
    }
}
