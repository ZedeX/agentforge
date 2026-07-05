package com.agent.runtime.repository;

import com.agent.runtime.model.TokenUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * TokenUsageLog JPA Repository（doc 06-runtime §7.1）。
 */
@Repository
public interface TokenUsageLogRepository extends JpaRepository<TokenUsageLog, Long> {

    /** 按会话 ID 查询用量日志 */
    List<TokenUsageLog> findBySessionId(String sessionId);

    /** 按 agent_instance_id 查询用量日志 */
    List<TokenUsageLog> findByAgentInstanceId(String agentInstanceId);

    /** 按 step_id 查询用量日志 */
    List<TokenUsageLog> findByStepId(String stepId);

    /** 聚合查询会话总 token 用量 */
    @Query("SELECT COALESCE(SUM(t.totalTokens), 0) FROM TokenUsageLog t WHERE t.sessionId = :sessionId")
    long sumTokensBySessionId(@Param("sessionId") String sessionId);

    /** 聚合查询 agent_instance 总 token 用量 */
    @Query("SELECT COALESCE(SUM(t.totalTokens), 0) FROM TokenUsageLog t WHERE t.agentInstanceId = :agentInstanceId")
    long sumTokensByAgentInstanceId(@Param("agentInstanceId") String agentInstanceId);
}
