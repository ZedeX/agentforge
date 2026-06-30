package com.agent.planning.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Plan validation result (doc 03-task-engine §8.2.1 ValidatePlanResponse).
 *
 * <p>5 dimensions: completeness / atomicity / efficiency / cost / fault-tolerance.
 * passed=true only if no errors. Warnings do not affect pass/fail.</p>
 */
public class PlanValidationResult {

    private boolean passed;
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<String> dimensions;

    public PlanValidationResult() {
    }

    public PlanValidationResult(boolean passed, List<String> errors, List<String> warnings) {
        this.passed = passed;
        this.errors = errors != null ? errors : new ArrayList<>();
        this.warnings = warnings != null ? warnings : new ArrayList<>();
    }

    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }

    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }

    public List<String> getDimensions() { return dimensions; }
    public void setDimensions(List<String> dimensions) { this.dimensions = dimensions; }

    /** Convenience: add an error and mark not passed. */
    public void addError(String error) {
        if (error != null) {
            errors.add(error);
            passed = false;
        }
    }

    /** Convenience: add a warning (does not affect pass status). */
    public void addWarning(String warning) {
        if (warning != null) {
            warnings.add(warning);
        }
    }
}
