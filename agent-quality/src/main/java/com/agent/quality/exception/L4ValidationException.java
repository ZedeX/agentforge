package com.agent.quality.exception;

import com.agent.quality.enums.L4ValidationResult;

/**
 * Thrown when L4 validation fails (FORMAT_VIOLATION / FACT_INCONSISTENCY / AUDIT_REJECTED).
 * Ref: doc 11-detail-flow F9.D2/D3/D4 false branches, UT-QA-002/003/005/007.
 */
public class L4ValidationException extends RuntimeException {

    private final L4ValidationResult validationResult;

    public L4ValidationException(L4ValidationResult result, String detail) {
        super(result.getCode() + ": " + detail);
        this.validationResult = result;
    }

    public L4ValidationResult getValidationResult() {
        return validationResult;
    }
}
