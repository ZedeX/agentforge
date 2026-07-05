package com.agent.runtime.repository;

import com.agent.runtime.model.AgentSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AgentSession JPA Repository（doc 06-runtime §7.1）。
 */
@Repository
public interface AgentSessionRepository extends JpaRepository<AgentSession, Long> {

    /** 按 agent_instance_id 查询 */
    Optional<AgentSession> findByAgentInstanceId(String agentInstanceId);

    /** 按 session_id 查询 */
    Optional<AgentSession> findBySessionId(String sessionId);

    /** 按 task_id + 状态查询 */
    List<AgentSession> findByTaskIdAndStatus(String taskId, com.agent.runtime.enums.SessionStatus status);

    /** 按 task_id 查询所有会话 */
    List<AgentSession> findByTaskId(String taskId);

    /** 单条更新状态（updatedAt 由 @Version 自动管理） */
    @Modifying
    @Query("UPDATE AgentSession s SET s.status = :status WHERE s.agentInstanceId = :agentInstanceId")
    int updateStatusByAgentInstanceId(@Param("agentInstanceId") String agentInstanceId,
                                       @Param("status") com.agent.runtime.enums.SessionStatus status);
}
