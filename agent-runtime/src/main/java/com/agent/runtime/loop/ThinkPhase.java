package com.agent.runtime.loop;

import com.agent.runtime.api.ModelGatewayClient;
import com.agent.runtime.api.TokenWatermarkMonitor;
import com.agent.runtime.enums.TokenLevel;
import com.agent.runtime.model.ReActContext;
import com.agent.runtime.model.StepState;
import com.agent.runtime.model.TokenWatermark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Think phase executor (T3, doc 06-runtime §2 F6 think).
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Build prompt via {@link ReActPromptBuilder}</li>
 *   <li>Call {@code ModelGatewayClient.chat(prompt)} to get model output</li>
 *   <li>Parse output to detect {@code final_answer:...} or {@code tool_call(toolId, args)}</li>
 *   <li>Update token watermark via {@link TokenWatermarkMonitor}</li>
 *   <li>Return {@link ThinkResult} for loop controller</li>
 * </ol>
 *
 * <p>Output grammar (mock-friendly, no JSON dependency):
 * <pre>
 *   final_answer:<text>           → ThinkResult.finalAnswer(...)
 *   tool_call(<id>, <args_json>)  → ThinkResult.toolCall(...)
 *   other                         → treat as final_answer fallback
 * </pre>
 */
@Component
public class ThinkPhase {

    private static final Logger log = LoggerFactory.getLogger(ThinkPhase.class);

    /** Matches {@code final_answer:<any text>} */
    private static final Pattern FINAL_ANSWER_PATTERN =
            Pattern.compile("final_answer\\s*:\\s*(.+)", Pattern.DOTALL);

    /** Matches {@code tool_call(toolId, argsJson)} */
    private static final Pattern TOOL_CALL_PATTERN =
            Pattern.compile("tool_call\\s*\\(\\s*([^,\\s]+)\\s*,\\s*(\\{.*?\\})\\s*\\)", Pattern.DOTALL);

    /** Default token cost for mock model calls (no real usage signal yet) */
    private static final int DEFAULT_MOCK_TOKEN_USAGE = 100;

    private final ModelGatewayClient modelGateway;
    private final ReActPromptBuilder promptBuilder;
    private final TokenWatermarkMonitor watermarkMonitor;

    public ThinkPhase(ModelGatewayClient modelGateway,
                      ReActPromptBuilder promptBuilder,
                      TokenWatermarkMonitor watermarkMonitor) {
        this.modelGateway = modelGateway;
        this.promptBuilder = promptBuilder;
        this.watermarkMonitor = watermarkMonitor;
    }

    /**
     * Execute Think phase for current step.
     *
     * @param ctx           ReAct context
     * @param history       accumulated past steps
     * @param recalledMemory distilled memory (may be empty)
     * @return ThinkResult containing final answer or tool call decision
     */
    public ThinkResult execute(ReActContext ctx, List<StepState> history, String recalledMemory) {
        String prompt = promptBuilder.build(ctx, history, recalledMemory);
        log.info("Think phase: agentInstanceId={}, stepNumber={}, promptLen={}",
                ctx.getAgentInstanceId(), ctx.getStepNumber(), prompt.length());

        String modelOutput;
        try {
            modelOutput = modelGateway.chat(prompt);
        } catch (RuntimeException ex) {
            log.warn("Think phase model call failed: agentInstanceId={}, error={}",
                    ctx.getAgentInstanceId(), ex.getMessage());
            return ThinkResult.failure(ex.getMessage(), DEFAULT_MOCK_TOKEN_USAGE);
        }

        if (modelOutput == null) {
            log.warn("Think phase model output null, treating as failure");
            return ThinkResult.failure("model_output_null", DEFAULT_MOCK_TOKEN_USAGE);
        }

        // Update token watermark
        int tokenUsage = estimateTokenUsage(prompt, modelOutput);
        TokenLevel level = watermarkMonitor.checkLevel(
                ctx.getTokenUsed() + tokenUsage, ctx.getTokenBudget());
        log.debug("Token watermark after Think: used={}, budget={}, level={}",
                ctx.getTokenUsed() + tokenUsage, ctx.getTokenBudget(), level);

        // Parse output
        ThinkResult result = parseModelOutput(modelOutput, tokenUsage);
        log.info("Think phase done: agentInstanceId={}, finished={}, hasToolCall={}",
                ctx.getAgentInstanceId(), result.isFinished(), result.getToolCallDecision() != null);
        return result;
    }

    /** Parse model output into ThinkResult. */
    ThinkResult parseModelOutput(String modelOutput, int tokenUsage) {
        // 1. final_answer pattern
        Matcher finalMatcher = FINAL_ANSWER_PATTERN.matcher(modelOutput);
        if (finalMatcher.find()) {
            String finalAnswer = finalMatcher.group(1).trim();
            return ThinkResult.finalAnswer(modelOutput, finalAnswer, tokenUsage, 0L);
        }

        // 2. tool_call pattern
        Matcher toolMatcher = TOOL_CALL_PATTERN.matcher(modelOutput);
        if (toolMatcher.find()) {
            String toolId = toolMatcher.group(1).trim();
            String argsJson = toolMatcher.group(2).trim();
            ToolCallDecision decision = new ToolCallDecision(toolId, argsJson);
            return ThinkResult.toolCall(modelOutput, decision, tokenUsage, 0L);
        }

        // 3. Fallback: treat entire output as final answer (lenient parsing)
        log.debug("Think output matched neither pattern, falling back to final_answer");
        return ThinkResult.finalAnswer(modelOutput, modelOutput, tokenUsage, 0L);
    }

    /** Estimate token usage for mock/prototype (1 token ≈ 4 chars). */
    private int estimateTokenUsage(String prompt, String output) {
        int promptTokens = (prompt.length() + 3) / 4;
        int outputTokens = (output.length() + 3) / 4;
        return promptTokens + outputTokens;
    }

    /** Compute TokenWatermark snapshot given current usage (exposed for loop controller). */
    public TokenWatermark snapshotWatermark(ReActContext ctx) {
        return new TokenWatermark(ctx.getTokenUsed(), ctx.getTokenBudget());
    }
}
