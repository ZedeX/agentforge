package com.agent.runtime.api.dto;

/**
 * Tool invocation result DTO (T5, doc 06-runtime §4).
 *
 * <p>Maps from {@code agentplatform.tool.v1.ToolInvokeResponse}: callId + status +
 * outputJson + errorCode + errorMsg + durationMs + costCent + tokenUsed + fromCache.
 *
 * <p>Status values: {@code success | failed | timeout | blocked}.
 */
public class ToolInvokeResult {

    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_TIMEOUT = "timeout";
    public static final String STATUS_BLOCKED = "blocked";

    private final String callId;
    private final String status;
    private final String outputJson;
    private final String errorCode;
    private final String errorMsg;
    private final int durationMs;
    private final long costCent;
    private final int tokenUsed;
    private final boolean fromCache;

    public ToolInvokeResult(String callId, String status, String outputJson,
                            String errorCode, String errorMsg,
                            int durationMs, long costCent, int tokenUsed, boolean fromCache) {
        this.callId = callId;
        this.status = status;
        this.outputJson = outputJson;
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
        this.durationMs = durationMs;
        this.costCent = costCent;
        this.tokenUsed = tokenUsed;
        this.fromCache = fromCache;
    }

    public boolean isSuccess() { return STATUS_SUCCESS.equals(status); }
    public boolean isFromCache() { return fromCache; }

    public String getCallId() { return callId; }
    public String getStatus() { return status; }
    public String getOutputJson() { return outputJson; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMsg() { return errorMsg; }
    public int getDurationMs() { return durationMs; }
    public long getCostCent() { return costCent; }
    public int getTokenUsed() { return tokenUsed; }
}
