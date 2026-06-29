package com.agent.tool.engine.api;

import com.agent.tool.engine.enums.ToolRiskLevel;
import com.agent.tool.engine.model.ToolMeta;

/**
 * Risk classifier port (F8 risk classification R1/R2/R3).
 *
 * <p>Classification rule (doc 08 §2):</p>
 * <ul>
 *   <li>R1: executor=general, side_effect=none</li>
 *   <li>R2: executor=proxy, side_effect=reversible</li>
 *   <li>R3: executor=sandbox, side_effect=irreversible</li>
 * </ul>
 */
public interface RiskClassifier {

    /**
     * Classify tool into R1/R2/R3 risk level.
     */
    ToolRiskLevel classify(ToolMeta meta);
}
