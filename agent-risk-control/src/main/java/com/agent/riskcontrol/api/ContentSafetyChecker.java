package com.agent.riskcontrol.api;

import com.agent.riskcontrol.model.CheckContentRequest;
import com.agent.riskcontrol.model.CheckContentResponse;

/**
 * Content safety checking port.
 *
 * <p>Checks content for sensitive words, PII, injection patterns, and harmful content.
 */
public interface ContentSafetyChecker {

    /**
     * Check content for safety violations.
     *
     * @param request check request containing content and check categories
     * @return check response with violations and sanitized content
     */
    CheckContentResponse check(CheckContentRequest request);
}
