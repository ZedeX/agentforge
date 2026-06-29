package com.agent.quality.enums;

/**
 * L4 三级校验结果枚举 (doc 11-detail-flow F9, PRD §三(一)2).
 *
 * <p>PASS: 校验通过
 * FORMAT_VIOLATION: F9.D2 硬校验失败（JSON Schema 非法 / 缺少来源标签 / 命中黑名单词）
 * FACT_INCONSISTENCY: F9.D3 事实一致性校验失败（cosine_sim &lt; 0.75）
 * AUDIT_REJECTED: F9.D4 强模型终审失败（overall &lt; 0.7）</p>
 */
public enum L4ValidationResult {

    PASS(1, "PASS"),
    FORMAT_VIOLATION(2, "FORMAT_VIOLATION"),
    FACT_INCONSISTENCY(3, "FACT_INCONSISTENCY"),
    AUDIT_REJECTED(4, "AUDIT_REJECTED");

    private final int level;
    private final String code;

    L4ValidationResult(int level, String code) {
        this.level = level;
        this.code = code;
    }

    public int getLevel() {
        return level;
    }

    public String getCode() {
        return code;
    }

    /**
     * 根据 code 字符串解析为 L4ValidationResult.
     *
     * @param code 结果码（PASS / FORMAT_VIOLATION / FACT_INCONSISTENCY / AUDIT_REJECTED）
     * @return L4ValidationResult 枚举值
     * @throws IllegalArgumentException 当 code 不匹配任何枚举值时抛出
     */
    public static L4ValidationResult fromCode(String code) {
        for (L4ValidationResult r : values()) {
            if (r.code.equals(code)) {
                return r;
            }
        }
        throw new IllegalArgumentException("Unknown L4ValidationResult code: " + code);
    }
}
