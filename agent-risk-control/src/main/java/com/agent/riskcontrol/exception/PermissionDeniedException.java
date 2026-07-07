package com.agent.riskcontrol.exception;

import com.agent.common.exception.ErrorCode;

/**
 * Exception thrown when permission is denied (HTTP 403).
 */
public class PermissionDeniedException extends RiskControlException {

    public PermissionDeniedException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
