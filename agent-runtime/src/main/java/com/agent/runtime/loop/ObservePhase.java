package com.agent.runtime.loop;

import com.agent.runtime.model.ReActContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Observe phase executor (T3, doc 06-runtime §2 F6 observe).
 *
 * <p>Responsibility: summarize {@link ActResult} into an observation string
 * that will be injected into the next Think prompt via history.</p>
 *
 * <p>Format:
 * <ul>
 *   <li>Success: {@code "[tool:<toolId>] <output>"}</li>
 *   <li>Failure: {@code "[tool:<toolId>] ERROR: <errorMessage>"}</li>
 * </ul>
 */
@Component
public class ObservePhase {

    private static final Logger log = LoggerFactory.getLogger(ObservePhase.class);

    /** Truncate observation to this length to avoid context bloat. */
    private static final int OBSERVATION_MAX_LEN = 1024;

    public ObservePhase() {
    }

    /**
     * Execute Observe phase: format ActResult into observation text.
     *
     * @param ctx       ReAct context
     * @param actResult result of preceding Act phase
     * @return observation text (truncated to {@link #OBSERVATION_MAX_LEN})
     */
    public String execute(ReActContext ctx, ActResult actResult) {
        if (actResult == null) {
            log.warn("Observe phase: actResult null, agentInstanceId={}",
                    ctx.getAgentInstanceId());
            return "[observe] empty_act_result";
        }

        String toolId = actResult.getToolId() != null ? actResult.getToolId() : "unknown";
        String observation;
        if (actResult.isSuccess()) {
            observation = "[tool:" + toolId + "] " + safe(actResult.getOutput());
        } else {
            observation = "[tool:" + toolId + "] ERROR: " + safe(actResult.getErrorMessage());
        }

        String truncated = truncate(observation);
        log.info("Observe phase: agentInstanceId={}, stepNumber={}, obsLen={}",
                ctx.getAgentInstanceId(), ctx.getStepNumber(), truncated.length());
        return truncated;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        if (s.length() <= OBSERVATION_MAX_LEN) {
            return s;
        }
        return s.substring(0, OBSERVATION_MAX_LEN) + "...(truncated)";
    }
}
