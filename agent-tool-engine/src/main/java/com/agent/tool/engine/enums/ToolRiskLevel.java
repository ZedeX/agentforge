package com.agent.tool.engine.enums;

/**
 * Tool risk level (doc 02-api §3.1 riskLevel, F8 risk classification).
 *
 * <p>R1 (general): low risk, local direct exec, no approval, no sandbox.
 * R2 (proxy): medium risk, proxy forward to business system, no approval, no sandbox.
 * R3 (sandbox): high risk, sandbox exec + dual approval.</p>
 */
public enum ToolRiskLevel {

    R1(1, "R1", false, false),
    R2(2, "R2", false, false),
    R3(3, "R3", true, true);

    private final int level;
    private final String code;
    private final boolean requiresApproval;
    private final boolean requiresSandbox;

    ToolRiskLevel(int level, String code, boolean requiresApproval, boolean requiresSandbox) {
        this.level = level;
        this.code = code;
        this.requiresApproval = requiresApproval;
        this.requiresSandbox = requiresSandbox;
    }

    public int getLevel() {
        return level;
    }

    public String getCode() {
        return code;
    }

    public boolean requiresApproval() {
        return requiresApproval;
    }

    public boolean requiresSandbox() {
        return requiresSandbox;
    }

    public static ToolRiskLevel fromCode(String code) {
        for (ToolRiskLevel r : values()) {
            if (r.code.equals(code)) {
                return r;
            }
        }
        throw new IllegalArgumentException("Unknown ToolRiskLevel: " + code);
    }
}
