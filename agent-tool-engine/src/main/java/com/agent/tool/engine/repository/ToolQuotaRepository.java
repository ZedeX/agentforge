package com.agent.tool.engine.repository;

import com.agent.tool.engine.entity.ToolQuotaEntity;
import com.agent.tool.engine.enums.QuotaSubjectType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * ToolQuota JPA Repository (Plan 05 T2).
 *
 * <p>提供按 subjectType + subjectId + toolId 维度的查询和配额扣减,
 * 供 ToolGatewayImpl (T8) 配额校验使用.</p>
 */
@Repository
public interface ToolQuotaRepository extends JpaRepository<ToolQuotaEntity, Long> {

    /** 按主体 + 工具查询精确配额. */
    Optional<ToolQuotaEntity> findBySubjectTypeAndSubjectIdAndToolId(
            QuotaSubjectType subjectType, String subjectId, String toolId);

    /** 按主体查询所有配额（含全工具配额）. */
    List<ToolQuotaEntity> findBySubjectTypeAndSubjectId(
            QuotaSubjectType subjectType, String subjectId);

    /** 查询需要重置的配额（resetAt 早于指定时间）. */
    List<ToolQuotaEntity> findByResetAtBefore(Instant resetBefore);

    /**
     * 原子扣减调用量配额（乐观锁 + 条件扣减, 返回受影响行数 0/1）.
     * 条件: daily_used < daily_limit AND version = :version
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ToolQuotaEntity q SET q.dailyUsed = q.dailyUsed + 1, " +
            "q.costUsedCent = q.costUsedCent + :costCent " +
            "WHERE q.id = :id AND q.version = :version AND q.dailyUsed < q.dailyLimit")
    int tryConsumeQuota(@Param("id") Long id, @Param("version") int version,
                        @Param("costCent") long costCent);

    /**
     * 重置已过期的配额记录（日切调度调用）.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ToolQuotaEntity q SET q.dailyUsed = 0, q.costUsedCent = 0, " +
            "q.resetAt = :nextReset WHERE q.resetAt < :now")
    int resetExpiredQuota(@Param("now") Instant now, @Param("nextReset") Instant nextReset);
}
