package com.agent.orchestrator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 事件消费日志实体（S-03 修复：RocketMQ 消费幂等）。
 *
 * <p>使用 JPA 唯一约束替代原 SubtaskDoneHandler 中的内存 ConcurrentHashMap.newKeySet()，
 * 解决四个问题：跨 Pod 共享 / 无界增长 OOM / 重启丢失 / 与业务写非事务。
 * 通过 event_id 列的 UNIQUE 约束，在同一 @Transactional 内与业务写入原子提交，
 * 捕获 DataIntegrityViolationException 处理多 Pod 并发竞争。</p>
 */
@Entity
@Table(name = "event_consume_log", uniqueConstraints = @UniqueConstraint(columnNames = "event_id"))
public class EventConsumeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }
}
