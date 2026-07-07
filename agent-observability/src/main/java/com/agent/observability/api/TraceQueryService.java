package com.agent.observability.api;

import com.agent.observability.model.GetTracesRequest;
import com.agent.observability.model.GetTracesResponse;

/**
 * Trace query port.
 *
 * <p>Queries distributed trace data from the repository.
 */
public interface TraceQueryService {

    /**
     * Query traces based on the request parameters.
     *
     * @param request trace query request
     * @return traces response with trace summaries
     */
    GetTracesResponse query(GetTracesRequest request);
}
