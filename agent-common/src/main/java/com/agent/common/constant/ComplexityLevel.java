package com.agent.common.constant;

/**
 * 任务复杂度等级（doc 02-api §1.3 complexity）
 * - L1 简单：单步或少步操作，低预算
 * - L2 中等：少量工具调用与多步推理
 * - L3 复杂：多工具编排、长链路、高预算
 */
public enum ComplexityLevel {

    L1(1, "L1", 5, 3, 500L),
    L2(2, "L2", 10, 5, 2000L),
    L3(3, "L3", 30, 10, 10000L);

    private final int level;
    private final String code;
    private final int stepRange;       // 推荐最大步数
    private final int toolRange;       // 推荐最大工具数
    private final long costLimitCent;  // 默认成本上限（分）

    ComplexityLevel(int level, String code, int stepRange, int toolRange, long costLimitCent) {
        this.level = level;
        this.code = code;
        this.stepRange = stepRange;
        this.toolRange = toolRange;
        this.costLimitCent = costLimitCent;
    }

    public int getLevel() {
        return level;
    }

    public String getCode() {
        return code;
    }

    public int getStepRange() {
        return stepRange;
    }

    public int getToolRange() {
        return toolRange;
    }

    public long getCostLimitCent() {
        return costLimitCent;
    }

    public static ComplexityLevel fromLevel(int level) {
        for (ComplexityLevel c : values()) {
            if (c.level == level) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unknown ComplexityLevel: " + level);
    }
}
