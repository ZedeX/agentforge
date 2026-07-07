package com.agent.observability.api;

import com.agent.observability.model.GetMetricsRequest;
import com.agent.observability.model.GetMetricsResponse;

/**
 * Metrics collection port.
 *
 * <p>Queries metric data from the repository.
 */
public interface MetricsCollector {

    /**
     * Collect metrics based on the request parameters.
     *
     * @param request metrics query request
     * @return metrics response with data points
     */
    GetMetricsResponse collect(GetMetricsRequest request);
}
