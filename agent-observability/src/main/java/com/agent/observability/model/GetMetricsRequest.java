package com.agent.observability.model;

import java.util.Map;

/**
 * POJO for GetMetrics request.
 */
public class GetMetricsRequest {

    private String serviceName;
    private String metricName;
    private long startTime;
    private long endTime;
    private String granularity;

    public GetMetricsRequest() {
    }

    public GetMetricsRequest(String serviceName, String metricName,
                             long startTime, long endTime, String granularity) {
        this.serviceName = serviceName;
        this.metricName = metricName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.granularity = granularity;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public String getGranularity() {
        return granularity;
    }

    public void setGranularity(String granularity) {
        this.granularity = granularity;
    }
}
