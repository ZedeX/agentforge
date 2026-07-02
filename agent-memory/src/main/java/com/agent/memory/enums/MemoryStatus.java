package com.agent.memory.enums;

/**
 * Memory lifecycle status (doc 04-memory §3.2).
 *
 * <p>4-state lifecycle: RAW → ACTIVE → DISTILLED → ARCHIVED.
 */
public enum MemoryStatus {

    /** 原始：刚写入未处理。 */
    RAW,
    /** 活跃：可被召回。 */
    ACTIVE,
    /** 蒸馏：已合并为摘要。 */
    DISTILLED,
    /** 归档：TTL 过期或蒸馏后归档。 */
    ARCHIVED
}
