package com.agent.orchestrator.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 实体基类，封装共享的审计字段 created_at / updated_at。
 *
 * <p>使用 @MappedSuperclass，子类继承后表结构包含这两列。
 * 时间戳由子类的 @PrePersist 回调通过 {@link #touchTimestamps()} 触发填充，
 * @PreUpdate 在此基类统一处理 updated_at 刷新。</p>
 *
 * <p>设计说明：tenant_id 为分片键业务字段（非纯审计字段），保留在子类中，
 * 不抽取到 BaseEntity，避免破坏 TaskInstance 的 23 参全参构造签名。</p>
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    protected Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    protected Instant updatedAt;

    /**
     * 填充时间戳：createdAt 仅在为 null 时填充（支持手动指定场景），updatedAt 总是刷新。
     * 由子类 @PrePersist 方法调用。
     */
    protected void touchTimestamps() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
