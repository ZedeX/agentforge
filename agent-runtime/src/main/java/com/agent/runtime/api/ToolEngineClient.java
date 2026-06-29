package com.agent.runtime.api;

/**
 * Tool engine client stub (F6 act phase: execute tool call).
 * Real implementation should route to agent-tool-engine module.
 */
public interface ToolEngineClient {

    /**
     * Invoke tool with given args.
     *
     * @param toolId tool identifier
     * @param args tool input arguments (JSON)
     * @return tool execution result
     */
    String invoke(String toolId, String args);
}
