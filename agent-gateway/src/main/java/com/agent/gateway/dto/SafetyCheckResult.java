package com.agent.gateway.dto;

/**
 * 风控预检结果。
 *
 * @param verdict   PASS / BLOCK
 * @param errorCode BLOCK 时填 CONTENT_BLOCKED
 * @param reason    命中原因
 */
public record SafetyCheckResult(String verdict, String errorCode, String reason) {

    public boolean isBlocked() {
        return "BLOCK".equalsIgnoreCase(verdict);
    }
}
