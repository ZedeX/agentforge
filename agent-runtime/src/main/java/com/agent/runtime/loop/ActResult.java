package com.agent.runtime.loop;

/**
 * Result of Act phase (T3, doc 06-runtime §2 F6 act).
 *
 * <p>Wraps ToolEngineClient.invoke() outcome with success/failure flag,
 * raw output, and optional error message for Reflexion trigger.</p>
 */
public class ActResult {

    private boolean success;
    private String toolId;
    private String output;
    private String errorMessage;
    private long durationMs;

    public ActResult() {
    }

    public static ActResult success(String toolId, String output, long durationMs) {
        ActResult r = new ActResult();
        r.success = true;
        r.toolId = toolId;
        r.output = output;
        r.durationMs = durationMs;
        return r;
    }

    public static ActResult failure(String toolId, String errorMessage, long durationMs) {
        ActResult r = new ActResult();
        r.success = false;
        r.toolId = toolId;
        r.errorMessage = errorMessage;
        r.durationMs = durationMs;
        return r;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getToolId() { return toolId; }
    public void setToolId(String toolId) { this.toolId = toolId; }

    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}
