package com.agent.orchestrator.repository;

import com.agent.orchestrator.entity.EventConsumeLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 事件消费日志 Repository（S-03 修复：RocketMQ 消费幂等）。
 *
 * <p>提供 {@link #existsByEventId(String)} 快速路径检查，配合 event_id 唯一约束
 * 的 DataIntegrityViolationException 捕获处理多 Pod 并发竞争。</p>
 */
public interface EventConsumeLogRepository extends JpaRepository<EventConsumeLog, Long> {

    boolean existsByEventId(String eventId);
}
