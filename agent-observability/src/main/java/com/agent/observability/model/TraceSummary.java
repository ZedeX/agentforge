package com.agent.observability.model;

/**
 * POJO for a trace summary.
 */
public class TraceSummary {

    private String traceId;
    private String rootService;
    private long startTime;
    private int durationMs;
    private int spanCount;
    private String status;

    public TraceSummary() {
    }

    public TraceSummary(String traceId, String rootService,
                        long startTime, int durationMs, int spanCount, String status) {
        this.traceId = traceId;
        this.rootService = rootService;
        this.startTime = startTime;
        this.durationMs = durationMs;
        this.spanCount = spanCount;
        this.status = status;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getRootService() {
        return rootService;
    }

    public void setRootService(String rootService) {
        this.rootService = rootService;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(int durationMs) {
        this.durationMs = durationMs;
    }

    public int getSpanCount() {
        return spanCount;
    }

    public void setSpanCount(int spanCount) {
        this.spanCount = spanCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
