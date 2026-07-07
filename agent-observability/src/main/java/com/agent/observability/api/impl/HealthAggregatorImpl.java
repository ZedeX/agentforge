package com.agent.observability.api.impl;

import com.agent.observability.api.HealthAggregator;
import com.agent.observability.entity.ServiceHealthEntity;
import com.agent.observability.model.GetHealthRequest;
import com.agent.observability.model.GetHealthResponse;
import com.agent.observability.model.ServiceHealth;
import com.agent.observability.repository.ServiceHealthRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Health aggregator implementation.
 *
 * <p>Aggregates health from the service health repository.
 * Computes overall status from individual service statuses.
 */
@Slf4j
@Component
public class HealthAggregatorImpl implements HealthAggregator {

    private final ServiceHealthRepository repository;

    public HealthAggregatorImpl(ServiceHealthRepository repository) {
        this.repository = repository;
    }

    @Override
    public GetHealthResponse aggregate(GetHealthRequest request) {
        List<ServiceHealth> services = new ArrayList<>();

        if (request.getServiceNames() != null && !request.getServiceNames().isEmpty()) {
            // Query specific services
            for (String serviceName : request.getServiceNames()) {
                Optional<ServiceHealthEntity> entity = repository.findByServiceName(serviceName);
                entity.ifPresent(e -> services.add(toModel(e)));
            }
        } else {
            // Query all services
            List<ServiceHealthEntity> entities = repository.findAll();
            for (ServiceHealthEntity entity : entities) {
                services.add(toModel(entity));
            }
        }

        // Compute overall status
        String overallStatus = computeOverallStatus(services);

        log.info("Health aggregated: servicesQueried={} overallStatus={}",
                services.size(), overallStatus);

        return new GetHealthResponse(overallStatus, services);
    }

    private String computeOverallStatus(List<ServiceHealth> services) {
        if (services.isEmpty()) {
            return "healthy";
        }

        boolean anyDown = false;
        boolean anyDegraded = false;

        for (ServiceHealth s : services) {
            if ("down".equals(s.getStatus())) {
                anyDown = true;
            } else if ("degraded".equals(s.getStatus())) {
                anyDegraded = true;
            }
        }

        if (anyDown) {
            return "unhealthy";
        }
        if (anyDegraded) {
            return "degraded";
        }
        return "healthy";
    }

    private ServiceHealth toModel(ServiceHealthEntity entity) {
        return new ServiceHealth(
                entity.getServiceName(),
                entity.getStatus() != null ? entity.getStatus() : "up",
                entity.getUptimeSeconds() != null ? entity.getUptimeSeconds() : 0L,
                entity.getErrorRate() != null ? entity.getErrorRate() : 0.0,
                entity.getLatencyP95Ms() != null ? entity.getLatencyP95Ms() : 0.0,
                entity.getDetail() != null ? entity.getDetail() : ""
        );
    }
}
