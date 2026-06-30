package com.agent.planning.enums;

/**
 * Task complexity level (doc 03-task-engine §6.2 / assessor ComplexityLevel).
 *
 * <p>L1: 简单任务 (总分 ≤8), 跳规划直跑
 * L2: 中等任务 (总分 9-14), 走模板/AI 规划
 * L3: 复杂任务 (总分 >14 或风险高), 强制 AI 规划 + 人工审核</p>
 */
public enum PlanComplexity {

    L1("l1", "简单任务, 跳规划直跑", 1),
    L2("l2", "中等任务, 模板/AI 规划", 2),
    L3("l3", "复杂任务, 强制 AI 规划 + 人工审核", 3);

    private final String code;
    private final String description;
    private final int numeric;

    PlanComplexity(String code, String description, int numeric) {
        this.code = code;
        this.description = description;
        this.numeric = numeric;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public int getNumeric() {
        return numeric;
    }

    /**
     * Parse complexity from code string (case-insensitive).
     *
     * @param code complexity code (l1 / l2 / l3)
     * @return PlanComplexity enum, default L1 if null/unknown
     */
    public static PlanComplexity fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return L1;
        }
        String lower = code.toLowerCase();
        for (PlanComplexity c : values()) {
            if (c.code.equals(lower)) {
                return c;
            }
        }
        return L1;
    }

    /**
     * Parse complexity from numeric value.
     *
     * @param numeric complexity numeric (1 / 2 / 3)
     * @return PlanComplexity enum, default L1 if out of range
     */
    public static PlanComplexity fromNumeric(int numeric) {
        for (PlanComplexity c : values()) {
            if (c.numeric == numeric) {
                return c;
            }
        }
        return L1;
    }
}
