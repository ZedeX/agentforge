package com.agent.memory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/**
 * Memory extraction log (doc 01-database §2.1, Plan 03 T2).
 *
 * <p>记录每次记忆提取的统计信息（来源任务 / 提取条数 / 失败数 / 耗时）。
 */
@Getter
@Setter
@Entity
@Table(name = "memory_extract_log")
public class MemoryExtractLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 来源任务 ID。 */
    @Column(name = "task_id", nullable = false, length = 32)
    private String taskId;

    /** 提取条数。 */
    @Column(name = "extract_count", nullable = false)
    private int extractCount;

    /** 失败条数。 */
    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    /** 耗时（毫秒）。 */
    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    /** 创建时间。 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public MemoryExtractLog() {
    }

    public MemoryExtractLog(String taskId, int extractCount, int failedCount, long durationMs) {
        this.taskId = taskId;
        this.extractCount = extractCount;
        this.failedCount = failedCount;
        this.durationMs = durationMs;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
