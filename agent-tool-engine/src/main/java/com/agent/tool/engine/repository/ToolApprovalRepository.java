package com.agent.tool.engine.repository;

import com.agent.tool.engine.entity.ToolApprovalEntity;
import com.agent.tool.engine.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * ToolApproval JPA Repository (Plan 05 T2).
 *
 * <p>提供按 approvalId / toolId / taskId / status 等维度的查询,
 * 供 ApprovalStoreImpl (T5) 使用.</p>
 */
@Repository
public interface ToolApprovalRepository extends JpaRepository<ToolApprovalEntity, Long> {

    /** 按审批单 ID 查询. */
    Optional<ToolApprovalEntity> findByApprovalId(String approvalId);

    /** 按工具 ID 查询所有审批记录. */
    List<ToolApprovalEntity> findByToolId(String toolId);

    /** 按任务 ID 查询所有审批记录. */
    List<ToolApprovalEntity> findByTaskId(String taskId);

    /** 按状态查询. */
    List<ToolApprovalEntity> findByStatus(ApprovalStatus status);

    /** 按工具 ID + 状态查询. */
    List<ToolApprovalEntity> findByToolIdAndStatus(String toolId, ApprovalStatus status);

    /**
     * 查询指定工具的有效（已批准且未过期）审批记录.
     * 供 ApprovalStoreImpl.validate() 调用.
     */
    List<ToolApprovalEntity> findByToolIdAndStatusAndExpireAtAfter(
            String toolId, ApprovalStatus status, Instant now);

    /** 查询已过期但仍为 PENDING/APPROVED 状态的记录（定时任务清理用）. */
    List<ToolApprovalEntity> findByStatusInAndExpireAtBefore(
            List<ApprovalStatus> statuses, Instant expireBefore);

    /** 检查 approvalId 是否已存在. */
    boolean existsByApprovalId(String approvalId);
}
