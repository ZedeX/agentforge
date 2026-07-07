package com.agent.observability.model;

import java.util.ArrayList;
import java.util.List;

/**
 * POJO for GetMetrics response.
 */
public class GetMetricsResponse {

    private List<MetricDataPoint> dataPoints = new ArrayList<>();

    public GetMetricsResponse() {
    }

    public GetMetricsResponse(List<MetricDataPoint> dataPoints) {
        this.dataPoints = dataPoints != null ? dataPoints : new ArrayList<>();
    }

    public List<MetricDataPoint> getDataPoints() {
        return dataPoints;
    }

    public void setDataPoints(List<MetricDataPoint> dataPoints) {
        this.dataPoints = dataPoints;
    }
}
