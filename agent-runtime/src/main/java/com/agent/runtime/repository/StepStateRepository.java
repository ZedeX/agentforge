package com.agent.runtime.repository;

import com.agent.runtime.model.StepState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * StepState JPA Repository（doc 06-runtime §7.1）。
 */
@Repository
public interface StepStateRepository extends JpaRepository<StepState, Long> {

    /** 按业务主键 step_id 查询 */
    Optional<StepState> findByStepId(String stepId);

    /** 按会话 ID 查询，按 stepNumber 升序 */
    List<StepState> findBySessionIdOrderByStepNumberAsc(String sessionId);

    /** 按会话 ID + 状态查询 */
    List<StepState> findBySessionIdAndStatus(String sessionId, com.agent.runtime.enums.StepStatus status);

    /** 按 agent_instance_id 查询，按 stepNumber 升序 */
    List<StepState> findByAgentInstanceIdOrderByStepNumberAsc(String agentInstanceId);

    /** 取会话最新一步（按 stepNumber 降序取第一个） */
    Optional<StepState> findFirstBySessionIdOrderByStepNumberDesc(String sessionId);

    /** 按 agent_instance_id 取最新一步 */
    Optional<StepState> findFirstByAgentInstanceIdOrderByStepNumberDesc(String agentInstanceId);

    /** 按 step_id 删除 */
    @Modifying
    void deleteByStepId(String stepId);

    /** 单条更新状态（updatedAt 由 @Version 自动管理） */
    @Modifying
    @Query("UPDATE StepState s SET s.status = :status WHERE s.stepId = :stepId")
    int updateStatusByStepId(@Param("stepId") String stepId, @Param("status") com.agent.runtime.enums.StepStatus status);
}
