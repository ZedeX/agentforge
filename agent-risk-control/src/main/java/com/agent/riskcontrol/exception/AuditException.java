package com.agent.riskcontrol.exception;

import com.agent.common.exception.ErrorCode;

/**
 * Exception thrown when audit logging fails (HTTP 500).
 */
public class AuditException extends RiskControlException {

    public AuditException(String message) {
        super(ErrorCode.INTERNAL, message);
    }

    public AuditException(String message, Throwable cause) {
        super(ErrorCode.INTERNAL, message, cause);
    }
}
