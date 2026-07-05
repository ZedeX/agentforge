package com.agent.runtime.loop;

import com.agent.runtime.api.ToolEngineClient;
import com.agent.runtime.model.ReActContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Act phase executor (T3, doc 06-runtime §2 F6 act).
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Extract toolId + paramsJson from {@link ToolCallDecision}</li>
 *   <li>Call {@code ToolEngineClient.invoke(toolId, argsJson)}</li>
 *   <li>Catch tool execution exceptions → return {@link ActResult#failure}</li>
 *   <li>Return {@link ActResult} for Observe phase</li>
 * </ol>
 */
@Component
public class ActPhase {

    private static final Logger log = LoggerFactory.getLogger(ActPhase.class);

    private final ToolEngineClient toolEngine;

    public ActPhase(ToolEngineClient toolEngine) {
        this.toolEngine = toolEngine;
    }

    /**
     * Execute Act phase: invoke tool with given decision.
     *
     * @param ctx      ReAct context
     * @param decision parsed from Think phase output
     * @return ActResult containing tool output or error
     */
    public ActResult execute(ReActContext ctx, ToolCallDecision decision) {
        if (decision == null || !decision.isValid()) {
            log.warn("Act phase: invalid tool call decision, agentInstanceId={}",
                    ctx.getAgentInstanceId());
            return ActResult.failure(null, "invalid_tool_call_decision", 0L);
        }

        String toolId = decision.getToolId();
        String argsJson = decision.getParamsJson() != null ? decision.getParamsJson() : "{}";
        log.info("Act phase: agentInstanceId={}, stepNumber={}, toolId={}",
                ctx.getAgentInstanceId(), ctx.getStepNumber(), toolId);

        long startMs = System.currentTimeMillis();
        try {
            String output = toolEngine.invoke(toolId, argsJson);
            long duration = System.currentTimeMillis() - startMs;
            log.info("Act phase success: toolId={}, durationMs={}", toolId, duration);
            return ActResult.success(toolId, output, duration);
        } catch (RuntimeException ex) {
            long duration = System.currentTimeMillis() - startMs;
            log.warn("Act phase failure: toolId={}, durationMs={}, error={}",
                    toolId, duration, ex.getMessage());
            return ActResult.failure(toolId, ex.getMessage(), duration);
        }
    }
}
