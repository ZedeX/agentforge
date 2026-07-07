package com.agent.observability.model;

/**
 * POJO for a single service health status.
 */
public class ServiceHealth {

    private String serviceName;
    private String status;
    private long uptimeSeconds;
    private double errorRate;
    private double latencyP95Ms;
    private String detail;

    public ServiceHealth() {
    }

    public ServiceHealth(String serviceName, String status, long uptimeSeconds,
                         double errorRate, double latencyP95Ms, String detail) {
        this.serviceName = serviceName;
        this.status = status;
        this.uptimeSeconds = uptimeSeconds;
        this.errorRate = errorRate;
        this.latencyP95Ms = latencyP95Ms;
        this.detail = detail;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getUptimeSeconds() {
        return uptimeSeconds;
    }

    public void setUptimeSeconds(long uptimeSeconds) {
        this.uptimeSeconds = uptimeSeconds;
    }

    public double getErrorRate() {
        return errorRate;
    }

    public void setErrorRate(double errorRate) {
        this.errorRate = errorRate;
    }

    public double getLatencyP95Ms() {
        return latencyP95Ms;
    }

    public void setLatencyP95Ms(double latencyP95Ms) {
        this.latencyP95Ms = latencyP95Ms;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
