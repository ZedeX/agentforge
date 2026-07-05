package com.agent.tool.engine.repository;

import com.agent.tool.engine.entity.ToolApprovalEntity;
import com.agent.tool.engine.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * ToolApproval JPA Repository (Plan 05 T2 + T5).
 *
 * <p>提供按 approvalId / toolId / taskId / status / approver / tenant+paramsHash
 * 等维度的查询, 供 ApprovalStoreImpl (T5) 使用.</p>
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
     * 供 ApprovalStoreImpl.findValid() 调用.
     */
    List<ToolApprovalEntity> findByToolIdAndStatusAndExpireAtAfter(
            String toolId, ApprovalStatus status, Instant now);

    /** 查询已过期但仍为 PENDING/APPROVED 状态的记录（定时任务清理用）. */
    List<ToolApprovalEntity> findByStatusInAndExpireAtBefore(
            List<ApprovalStatus> statuses, Instant expireBefore);

    /** 检查 approvalId 是否已存在. */
    boolean existsByApprovalId(String approvalId);

    // ============ T5 新增: R2 近期审批查询 + 待审查询 ============

    /**
     * 查询指定租户 + 工具 + paramsHash 的近期已批准记录
     * (R2 近期审批跳过用, doc 05 §4.3).
     *
     * <p>paramsHash 为 null 时匹配任意 params (兼容旧调用).</p>
     */
    Optional<ToolApprovalEntity> findFirstByTenantIdAndToolIdAndParamsHashAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            String tenantId, String toolId, String paramsHash,
            ApprovalStatus status, Instant createdAtAfter);

    /**
     * 查询指定租户 + 工具的近期已批准记录 (paramsHash 任意).
     */
    Optional<ToolApprovalEntity> findFirstByTenantIdAndToolIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            String tenantId, String toolId, ApprovalStatus status, Instant createdAtAfter);

    /**
     * 按申请人查询待审批记录 (approver 视图).
     * 注: 当前 schema 没有 approver 字段分配机制, 暂用 applicant 查询.
     */
    List<ToolApprovalEntity> findByApplicantAndStatus(String applicant, ApprovalStatus status);

    /**
     * 批量将指定 ID 的记录状态更新为 newStatus (定时任务标记 EXPIRED 用).
     *
     * <p>需显式 JPQL — Spring Data JPA 无法从方法名派生 update 查询.</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ToolApprovalEntity a SET a.status = :newStatus WHERE a.id IN :ids")
    int updateStatusByIdIn(@Param("newStatus") ApprovalStatus newStatus, @Param("ids") List<Long> ids);
}
