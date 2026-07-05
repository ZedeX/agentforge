package com.agent.runtime.loop;

import com.agent.runtime.enums.ReflexionResult;
import com.agent.runtime.model.ReActContext;
import com.agent.runtime.model.StepState;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reflexion prompt builder (T6, doc 06-runtime §3.2 / §5.2).
 *
 * <p>Constructs prompts for optional LLM-based reflexion. The default {@code ReflexionEngineImpl}
 * uses rule-based 4-state policy; this builder enables a future LLM-based reflexion path
 * where the model evaluates history and emits one of {@code CONTINUE/RETRY/REPLAN/ABORT}.
 *
 * <p>Prompt structure:
 * <ol>
 *   <li>System header: explains reflexion task + 4 decision options</li>
 *   <li>Context: agentInstanceId / stepNumber / retryCount / maxRetry / triggerReason</li>
 *   <li>Recent history: last N steps (phase + status + brief content)</li>
 *   <li>Decision instruction: emit exactly one of CONTINUE/RETRY/REPLAN/ABORT + reason</li>
 * </ol>
 */
@Component
public class ReflexionPromptBuilder {

    /** Max steps to include in prompt history (avoid token bloat). */
    private static final int MAX_HISTORY_STEPS = 5;

    /** Token estimation: 1 token ≈ 4 chars (rough heuristic for budgeting). */
    private static final int CHARS_PER_TOKEN = 4;

    /**
     * Build reflexion prompt for LLM-based reflection.
     *
     * @param ctx           ReAct context
     * @param history       accumulated step history
     * @param triggerReason why reflexion was triggered (e.g. "act_failure", "interval:3", "token_yellow")
     * @return prompt string for LLM
     */
    public String build(ReActContext ctx, List<StepState> history, String triggerReason) {
        StringBuilder sb = new StringBuilder();

        // 1. System header
        sb.append("You are a Reflexion evaluator for an autonomous agent.\n");
        sb.append("Analyze the agent's recent history and decide the next action.\n\n");
        sb.append("Decision options (emit exactly one):\n");
        sb.append("- CONTINUE: proceed to next step\n");
        sb.append("- RETRY: re-run current step with adjusted approach\n");
        sb.append("- REPLAN: request orchestrator to replan task decomposition\n");
        sb.append("- ABORT: terminate session due to unrecoverable failure\n\n");

        // 2. Context
        sb.append("=== Context ===\n");
        sb.append("agentInstanceId: ").append(ctx.getAgentInstanceId()).append('\n');
        sb.append("stepNumber: ").append(ctx.getStepNumber()).append('\n');
        sb.append("retryCount: ").append(ctx.getRetryCount()).append('\n');
        sb.append("maxRetry: ").append(ctx.getMaxRetry()).append('\n');
        sb.append("triggerReason: ").append(triggerReason == null ? "unknown" : triggerReason).append('\n');
        sb.append("tokenUsed: ").append(ctx.getTokenUsed())
                .append(" / tokenBudget: ").append(ctx.getTokenBudget()).append('\n');
        sb.append("userInput: ").append(truncate(ctx.getUserInput(), 200)).append('\n');
        sb.append('\n');

        // 3. Recent history
        sb.append("=== Recent History ===\n");
        if (history == null || history.isEmpty()) {
            sb.append("(no prior steps)\n");
        } else {
            int start = Math.max(0, history.size() - MAX_HISTORY_STEPS);
            for (int i = start; i < history.size(); i++) {
                StepState s = history.get(i);
                sb.append("Step ").append(s.getStepNo()).append(": ");
                sb.append("phase=").append(s.getPhase() == null ? "?" : s.getPhase());
                sb.append(", status=").append(s.getStatus() == null ? "?" : s.getStatus());
                if (s.getThinkContent() != null) {
                    sb.append(", think=").append(truncate(s.getThinkContent(), 100));
                }
                if (s.getObserveContent() != null) {
                    sb.append(", observe=").append(truncate(s.getObserveContent(), 100));
                }
                if (s.getErrorMessage() != null) {
                    sb.append(", error=").append(truncate(s.getErrorMessage(), 100));
                }
                sb.append('\n');
            }
        }
        sb.append('\n');

        // 4. Decision instruction
        sb.append("=== Your Decision ===\n");
        sb.append("Emit exactly one line in the format: DECISION:<one of CONTINUE|RETRY|REPLAN|ABORT>:<reason>\n");
        sb.append("Example: DECISION:RETRY:tool_call returned invalid JSON, retry with corrected schema\n");

        return sb.toString();
    }

    /** Estimate token count for the constructed prompt (rough char-based heuristic). */
    public int estimateTokens(String prompt) {
        if (prompt == null || prompt.isEmpty()) return 0;
        return (prompt.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
    }

    /** Map LLM output line to ReflexionResult enum (lenient parsing). */
    public ReflexionResult parseDecision(String modelOutput) {
        if (modelOutput == null) return ReflexionResult.CONTINUE;
        String upper = modelOutput.toUpperCase();
        if (upper.contains("DECISION:ABORT") || upper.contains("ABORT")) {
            return ReflexionResult.ABORT;
        }
        if (upper.contains("DECISION:REPLAN") || upper.contains("REPLAN")) {
            return ReflexionResult.REPLAN;
        }
        if (upper.contains("DECISION:RETRY") || upper.contains("RETRY")) {
            return ReflexionResult.RETRY;
        }
        return ReflexionResult.CONTINUE;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
