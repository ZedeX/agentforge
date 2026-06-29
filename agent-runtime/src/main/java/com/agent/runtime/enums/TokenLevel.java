package com.agent.runtime.enums;

/**
 * Token watermark level (doc 11-detail-flow F7, PRD §二(三)3 token compression).
 *
 * <p>SAFE: token ≤70%, 无压缩
 * WARN: token 70%~85%, 轻度压缩 (裁剪冗余字段)
 * CRITICAL: token 85%~95%, 中度压缩 (早期对话摘要化)
 * CIRCUIT_BREAK: token ≥95%, 重度压缩 (滑动窗口保留最近 K 轮)</p>
 */
public enum TokenLevel {

    SAFE(1, "SAFE", 0.0, 0.70),
    WARN(2, "WARN", 0.70, 0.85),
    CRITICAL(3, "CRITICAL", 0.85, 0.95),
    CIRCUIT_BREAK(4, "CIRCUIT_BREAK", 0.95, 1.01);

    private final int level;
    private final String code;
    private final double lowerBound;
    private final double upperBound;

    TokenLevel(int level, String code, double lowerBound, double upperBound) {
        this.level = level;
        this.code = code;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public int getLevel() {
        return level;
    }

    public String getCode() {
        return code;
    }

    public double getLowerBound() {
        return lowerBound;
    }

    public double getUpperBound() {
        return upperBound;
    }

    /**
     * 根据 token 使用比例返回对应水位级别.
     *
     * @param usageRatio token 使用比例 [0.0, 1.0]
     * @return TokenLevel (SAFE / WARN / CRITICAL / CIRCUIT_BREAK)
     */
    public static TokenLevel fromUsageRatio(double usageRatio) {
        for (TokenLevel level : values()) {
            if (usageRatio >= level.lowerBound && usageRatio < level.upperBound) {
                return level;
            }
        }
        // usageRatio >= 1.0 或异常值, 返回熔断
        return CIRCUIT_BREAK;
    }
}
