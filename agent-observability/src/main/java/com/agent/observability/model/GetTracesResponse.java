package com.agent.observability.model;

import java.util.ArrayList;
import java.util.List;

/**
 * POJO for GetTraces response.
 */
public class GetTracesResponse {

    private List<TraceSummary> traces = new ArrayList<>();

    public GetTracesResponse() {
    }

    public GetTracesResponse(List<TraceSummary> traces) {
        this.traces = traces != null ? traces : new ArrayList<>();
    }

    public List<TraceSummary> getTraces() {
        return traces;
    }

    public void setTraces(List<TraceSummary> traces) {
        this.traces = traces;
    }
}
