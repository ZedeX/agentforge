package com.agent.observability.model;

/**
 * POJO for GetTraces request.
 */
public class GetTracesRequest {

    private String traceId;
    private String serviceName;
    private long startTime;
    private long endTime;
    private int limit;

    public GetTracesRequest() {
    }

    public GetTracesRequest(String traceId, String serviceName,
                            long startTime, long endTime, int limit) {
        this.traceId = traceId;
        this.serviceName = serviceName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.limit = limit;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
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

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}
