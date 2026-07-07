package com.agent.observability.api.impl;

import com.agent.observability.api.TraceQueryService;
import com.agent.observability.config.ObservabilityProperties;
import com.agent.observability.entity.TraceEntity;
import com.agent.observability.model.GetTracesRequest;
import com.agent.observability.model.GetTracesResponse;
import com.agent.observability.model.TraceSummary;
import com.agent.observability.repository.TraceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Trace query service implementation.
 *
 * <p>Queries trace data from JPA repository.
 */
@Slf4j
@Component
public class TraceQueryServiceImpl implements TraceQueryService {

    private final TraceRepository repository;
    private final ObservabilityProperties properties;

    public TraceQueryServiceImpl(TraceRepository repository,
                                 ObservabilityProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public GetTracesResponse query(GetTracesRequest request) {
        // Query by specific trace ID
        if (request.getTraceId() != null && !request.getTraceId().isEmpty()) {
            Optional<TraceEntity> entity = repository.findByTraceId(request.getTraceId());
            if (entity.isEmpty()) {
                log.info("Trace not found: traceId={}", request.getTraceId());
                return new GetTracesResponse(List.of());
            }
            return new GetTracesResponse(List.of(toModel(entity.get())));
        }

        // Query by service name + time range
        int limit = request.getLimit() > 0
                ? Math.min(request.getLimit(), properties.getTrace().getMaxResults())
                : properties.getTrace().getMaxResults();

        Instant start = Instant.ofEpochMilli(request.getStartTime());
        Instant end = Instant.ofEpochMilli(request.getEndTime());

        List<TraceEntity> entities;
        if (request.getServiceName() != null && !request.getServiceName().isEmpty()) {
            entities = repository.findByRootServiceAndStartTimeBetween(
                    request.getServiceName(), start, end);
        } else {
            entities = repository.findByStartTimeBetween(start, end);
        }

        List<TraceSummary> traces = entities.stream()
                .limit(limit)
                .map(this::toModel)
                .collect(Collectors.toList());

        log.info("Traces queried: serviceName={} limit={} count={}",
                request.getServiceName(), limit, traces.size());

        return new GetTracesResponse(traces);
    }

    private TraceSummary toModel(TraceEntity entity) {
        return new TraceSummary(
                entity.getTraceId(),
                entity.getRootService(),
                entity.getStartTime() != null ? entity.getStartTime().toEpochMilli() : 0L,
                entity.getDurationMs() != null ? entity.getDurationMs() : 0,
                entity.getSpanCount() != null ? entity.getSpanCount() : 0,
                entity.getStatus() != null ? entity.getStatus() : "ok"
        );
    }
}
