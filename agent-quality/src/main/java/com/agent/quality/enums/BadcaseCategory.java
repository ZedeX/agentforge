package com.agent.quality.enums;

/**
 * Badcase 归类枚举 (doc 11-detail-flow F9, PRD §四(二)4).
 *
 * <p>HALLUCINATION: 幻觉（无来源捏造事实）
 * FORMAT_ERROR: 格式错误（缺少来源标签 / JSON Schema 非法）
 * FACT_INCONSISTENCY: 事实不一致（与参考源冲突）
 * AUDIT_REJECTED: 终审驳回（强模型四维度评分不达标）
 * OTHER: 其他类型</p>
 */
public enum BadcaseCategory {
    HALLUCINATION,
    FORMAT_ERROR,
    FACT_INCONSISTENCY,
    AUDIT_REJECTED,
    OTHER
}
