package com.agent.common.constant;

/**
 * 工具风险等级（doc 02-api §3.1 riskLevel，doc 00-overview §1.1 安全优先）
 *
 * - R1（general）：低风险，本地直接执行
 * - R2（proxy）：中风险，代理转发到业务系统
 * - R3（sandbox）：高风险，沙箱执行 + 双人复核
 */
public enum RiskLevel {

    R1(1, "R1", "general"),
    R2(2, "R2", "proxy"),
    R3(3, "R3", "sandbox");

    private final int level;
    private final String code;
    private final String executor;

    RiskLevel(int level, String code, String executor) {
        this.level = level;
        this.code = code;
        this.executor = executor;
    }

    public int getLevel() {
        return level;
    }

    public String getCode() {
        return code;
    }

    public String getExecutor() {
        return executor;
    }

    public static RiskLevel fromLevel(int level) {
        for (RiskLevel r : values()) {
            if (r.level == level) {
                return r;
            }
        }
        throw new IllegalArgumentException("Unknown RiskLevel: " + level);
    }
}
