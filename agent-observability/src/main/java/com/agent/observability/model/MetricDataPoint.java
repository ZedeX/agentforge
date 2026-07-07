package com.agent.observability.model;

import java.util.HashMap;
import java.util.Map;

/**
 * POJO for a single metric data point.
 */
public class MetricDataPoint {

    private String metricName;
    private String serviceName;
    private long timestamp;
    private double value;
    private Map<String, String> labels = new HashMap<>();

    public MetricDataPoint() {
    }

    public MetricDataPoint(String metricName, String serviceName,
                           long timestamp, double value, Map<String, String> labels) {
        this.metricName = metricName;
        this.serviceName = serviceName;
        this.timestamp = timestamp;
        this.value = value;
        this.labels = labels != null ? labels : new HashMap<>();
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }
}
