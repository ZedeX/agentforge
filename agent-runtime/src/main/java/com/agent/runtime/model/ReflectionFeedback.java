package com.agent.runtime.model;

/**
 * Reflexion feedback injected into next retry prompt (doc 11-detail-flow F9.D5).
 */
public class ReflectionFeedback {

    private String reflectionPrompt;
    private String validationFailure;
    private int retryAttempt;

    public ReflectionFeedback() {
    }

    public ReflectionFeedback(String reflectionPrompt, String validationFailure, int retryAttempt) {
        this.reflectionPrompt = reflectionPrompt;
        this.validationFailure = validationFailure;
        this.retryAttempt = retryAttempt;
    }

    public String getReflectionPrompt() { return reflectionPrompt; }
    public void setReflectionPrompt(String reflectionPrompt) { this.reflectionPrompt = reflectionPrompt; }

    public String getValidationFailure() { return validationFailure; }
    public void setValidationFailure(String validationFailure) { this.validationFailure = validationFailure; }

    public int getRetryAttempt() { return retryAttempt; }
    public void setRetryAttempt(int retryAttempt) { this.retryAttempt = retryAttempt; }
}
