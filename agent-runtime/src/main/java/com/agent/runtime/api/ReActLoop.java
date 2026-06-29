package com.agent.runtime.api;

import com.agent.runtime.model.ReActContext;

/**
 * ReAct loop controller (doc 06 §2, F6 think/act/observe/finish).
 */
public interface ReActLoop {

    /**
     * Start ReAct loop for given task context.
     *
     * @param context ReAct execution context
     * @return final answer string
     */
    String start(ReActContext context);

    /**
     * Transit to next phase based on model output.
     *
     * @param context current context
     * @param modelOutput model generation output (thought / tool_call / final_answer)
     * @return next phase
     */
    com.agent.runtime.enums.ReActPhaseType transit(ReActContext context, String modelOutput);
}
