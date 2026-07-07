package com.agent.observability.api;

import com.agent.observability.model.GetHealthRequest;
import com.agent.observability.model.GetHealthResponse;

/**
 * Health aggregation port.
 *
 * <p>Aggregates health status from known services.
 */
public interface HealthAggregator {

    /**
     * Aggregate health status for the requested services.
     *
     * @param request health query request
     * @return health response with overall status and per-service details
     */
    GetHealthResponse aggregate(GetHealthRequest request);
}
