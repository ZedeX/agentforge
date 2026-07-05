package com.agent.runtime.loop;

import com.agent.runtime.config.RuntimeProperties;
import com.agent.runtime.enums.ReActPhaseType;
import com.agent.runtime.model.ReActContext;
import com.agent.runtime.model.StepState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ReAct prompt assembler (T3, doc 06-runtime §2, F6 think phase).
 *
 * <p>Builds the prompt sent to ModelGateway by concatenating:
 * <ol>
 *   <li>System message describing Think/Act/Observe protocol</li>
 *   <li>Recalled memory snippets (distilled experience)</li>
 *   <li>History of past steps formatted as Thought/Action/Observation</li>
 *   <li>User query / task description</li>
 * </ol>
 *
 * <p>Output format is plain text so the mock {@code ModelGatewayClient.chat(prompt)}
 * can parse {@code final_answer:...} / {@code tool_call(toolId, args)} tokens.</p>
 */
@Component
public class ReActPromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(ReActPromptBuilder.class);

    private static final String SYSTEM_HEADER =
            "You are a ReAct agent. Use the Thought/Action/Observation pattern.\n" +
            "Respond in one of these forms:\n" +
            "  - Thought: <reasoning>\\nAction: tool_call(<toolId>, <args_json>)\n" +
            "  - Thought: <reasoning>\\nFinal: <final_answer>";

    private final RuntimeProperties properties;

    public ReActPromptBuilder(RuntimeProperties properties) {
        this.properties = properties;
    }

    /**
     * Build prompt for Think phase.
     *
     * @param ctx           ReAct context (carries user input, step number, token budget)
     * @param history       accumulated past steps (THINK/ACT/OBSERVE)
     * @param recalledMemory distilled memory text (may be empty)
     * @return assembled prompt string
     */
    public String build(ReActContext ctx, List<StepState> history, String recalledMemory) {
        StringBuilder sb = new StringBuilder();

        // 1. System header
        sb.append(SYSTEM_HEADER).append("\n\n");

        // 2. Recalled memory
        if (recalledMemory != null && !recalledMemory.isEmpty()) {
            sb.append("[RecalledMemory]\n").append(recalledMemory).append("\n\n");
        }

        // 3. Token budget hint
        sb.append("[Constraints] maxSteps=").append(ctx.getMaxSteps())
                .append(", tokenBudget=").append(ctx.getTokenBudget())
                .append(", currentStep=").append(ctx.getStepNumber())
                .append("\n\n");

        // 4. History
        if (history != null && !history.isEmpty()) {
            sb.append("[History]\n");
            for (StepState s : history) {
                appendHistoryEntry(sb, s);
            }
            sb.append("\n");
        }

        // 5. User query
        String query = ctx.getUserInput() != null ? ctx.getUserInput() : ctx.getTaskId();
        sb.append("[UserQuery] ").append(query).append("\n");

        String prompt = sb.toString();
        log.debug("Built ReAct prompt: agentInstanceId={}, stepNumber={}, promptLen={}",
                ctx.getAgentInstanceId(), ctx.getStepNumber(), prompt.length());
        return prompt;
    }

    private void appendHistoryEntry(StringBuilder sb, StepState s) {
        ReActPhaseType phase = s.getPhase();
        if (phase == null) {
            return;
        }
        switch (phase) {
            case THINK:
                sb.append("Thought: ").append(safe(s.getThinkContent())).append("\n");
                break;
            case ACT:
                sb.append("Action: tool_call(")
                        .append(safe(s.getActionTarget()))
                        .append(", ")
                        .append(safe(s.getInputJson()))
                        .append(")\n");
                break;
            case OBSERVE:
                sb.append("Observation: ").append(safe(s.getObserveContent())).append("\n");
                break;
            case FINISH:
                sb.append("Final: ").append(safe(s.getThinkContent())).append("\n");
                break;
            default:
                // ignore unknown phases
                break;
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
