package com.agent.tool.engine.api;

/**
 * Result cleaner port (F8 output processing: truncate + summarize).
 */
public interface ResultCleaner {

    /**
     * Clean and truncate raw output to fit maxToken limit.
     *
     * @param rawOutput raw tool output
     * @param maxToken  max token budget
     * @return cleaned output (<= maxToken); summarized when exceeding limit
     */
    String clean(String rawOutput, int maxToken);
}
