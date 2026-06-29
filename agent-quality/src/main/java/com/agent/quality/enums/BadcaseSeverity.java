package com.agent.quality.enums;

/**
 * Badcase 严重度枚举 (doc 11-detail-flow F9, PRD §四(二)4).
 *
 * <p>LOW: 低严重度，severityScore &lt; 0.5
 * MEDIUM: 中严重度，0.5 ≤ severityScore &lt; 0.8
 * HIGH: 高严重度，severityScore ≥ 0.8，需推送人工审核队列</p>
 */
public enum BadcaseSeverity {
    LOW,
    MEDIUM,
    HIGH
}
