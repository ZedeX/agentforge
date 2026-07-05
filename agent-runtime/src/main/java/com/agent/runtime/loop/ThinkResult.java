package com.agent.runtime.loop;

/**
 * Result of Think phase (T3, doc 06-runtime §2 F6 think).
 *
 * <p>Encapsulates model output: either a {@code finalAnswer} (loop terminates)
 * or a {@code toolCallDecision} (proceed to Act phase), plus token usage
 * for watermark accounting.</p>
 */
public class ThinkResult {

    /** Raw model output text (for logging / StepState.thinkContent) */
    private String thoughtContent;
    /** Parsed tool call decision; null when model gives final answer */
    private ToolCallDecision toolCallDecision;
    /** True when model outputs final_answer, loop should terminate */
    private boolean finished;
    /** Final answer text; non-null iff {@link #finished} is true */
    private String finalAnswer;
    /** Token usage of this Think call (prompt + completion) */
    private int tokenUsage;
    /** Cost in cents for this Think call */
    private long costCent;
    /** Error message if Think failed (e.g. model unavailable); null on success */
    private String errorMessage;

    public ThinkResult() {
    }

    public static ThinkResult finalAnswer(String thoughtContent, String finalAnswer, int tokenUsage, long costCent) {
        ThinkResult r = new ThinkResult();
        r.thoughtContent = thoughtContent;
        r.finalAnswer = finalAnswer;
        r.finished = true;
        r.tokenUsage = tokenUsage;
        r.costCent = costCent;
        return r;
    }

    public static ThinkResult toolCall(String thoughtContent, ToolCallDecision decision,
                                       int tokenUsage, long costCent) {
        ThinkResult r = new ThinkResult();
        r.thoughtContent = thoughtContent;
        r.toolCallDecision = decision;
        r.finished = false;
        r.tokenUsage = tokenUsage;
        r.costCent = costCent;
        return r;
    }

    public static ThinkResult failure(String errorMessage, int tokenUsage) {
        ThinkResult r = new ThinkResult();
        r.errorMessage = errorMessage;
        r.finished = false;
        r.tokenUsage = tokenUsage;
        return r;
    }

    public String getThoughtContent() { return thoughtContent; }
    public void setThoughtContent(String thoughtContent) { this.thoughtContent = thoughtContent; }

    public ToolCallDecision getToolCallDecision() { return toolCallDecision; }
    public void setToolCallDecision(ToolCallDecision toolCallDecision) { this.toolCallDecision = toolCallDecision; }

    public boolean isFinished() { return finished; }
    public void setFinished(boolean finished) { this.finished = finished; }

    public String getFinalAnswer() { return finalAnswer; }
    public void setFinalAnswer(String finalAnswer) { this.finalAnswer = finalAnswer; }

    public int getTokenUsage() { return tokenUsage; }
    public void setTokenUsage(int tokenUsage) { this.tokenUsage = tokenUsage; }

    public long getCostCent() { return costCent; }
    public void setCostCent(long costCent) { this.costCent = costCent; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public boolean isFailed() { return errorMessage != null; }
}
