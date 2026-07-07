package com.agent.riskcontrol.exception;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;

/**
 * Base exception for agent-risk-control module.
 */
public class RiskControlException extends BusinessException {

    public RiskControlException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public RiskControlException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
