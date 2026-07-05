package com.agent.tool.engine.repository;

import com.agent.tool.engine.entity.ToolCallLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * ToolCallLog JPA Repository (Plan 05 T2).
 *
 * <p>提供按 callId / taskId / toolId / status / 时间范围 等维度的查询,
 * 供 ToolCallAuditor (T9) 使用. 生产环境按月分表由 ShardingSphere 处理.</p>
 */
@Repository
public interface ToolCallLogRepository extends JpaRepository<ToolCallLogEntity, Long> {

    /** 按调用 ID 查询. */
    Optional<ToolCallLogEntity> findByCallId(String callId);

    /** 按任务 ID 查询所有调用记录. */
    List<ToolCallLogEntity> findByTaskId(String taskId);

    /** 按任务 ID + 步骤号查询. */
    List<ToolCallLogEntity> findByTaskIdOrderByStepNoAsc(String taskId);

    /** 按工具 ID + 状态查询. */
    List<ToolCallLogEntity> findByToolIdAndStatus(String toolId, String status);

    /** 按任务 ID 分页查询. */
    Page<ToolCallLogEntity> findByTaskId(String taskId, Pageable pageable);

    /** 按时间范围查询（用于审计报表）. */
    List<ToolCallLogEntity> findByCreatedAtBetween(Instant start, Instant end);

    /** 按工具 ID + 时间范围查询. */
    List<ToolCallLogEntity> findByToolIdAndCreatedAtBetween(String toolId, Instant start, Instant end);

    /** 按链路 ID 查询. */
    List<ToolCallLogEntity> findByTraceId(String traceId);

    /** 按任务 ID 计数. */
    long countByTaskId(String taskId);

    /** 按工具 ID + 状态计数. */
    long countByToolIdAndStatus(String toolId, String status);

    // ==================== T9 audit query methods ====================

    /** 按租户 ID + 时间范围分页查询 (T9). */
    Page<ToolCallLogEntity> findByTenantIdAndCreatedAtBetween(
            String tenantId, Instant start, Instant end, Pageable pageable);

    /** 按租户 ID + 工具 ID 分页查询 (T9). */
    Page<ToolCallLogEntity> findByTenantIdAndToolId(
            String tenantId, String toolId, Pageable pageable);

    /** 按租户 ID + 状态计数 (T9). */
    long countByTenantIdAndStatus(String tenantId, String status);
}
