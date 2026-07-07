package com.agent.riskcontrol.exception;

import com.agent.common.exception.ErrorCode;

/**
 * Exception thrown when content violates safety rules (HTTP 400).
 */
public class ContentViolationException extends RiskControlException {

    public ContentViolationException(String message) {
        super(ErrorCode.CONTENT_BLOCKED, message);
    }
}
