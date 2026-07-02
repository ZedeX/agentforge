package com.agent.memory.repository;

import com.agent.memory.enums.MemoryStatus;
import com.agent.memory.model.MemoryRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * MemoryRecord JPA Repository (Plan 03 T2).
 */
@Repository
public interface MemoryRecordRepository extends JpaRepository<MemoryRecord, Long> {

    /** 按业务 ID 查询。 */
    Optional<MemoryRecord> findByMemoryId(String memoryId);

    /** 按租户 + 状态查询。 */
    List<MemoryRecord> findByTenantIdAndStatus(String tenantId, MemoryStatus status);

    /** 按主题查询。 */
    List<MemoryRecord> findByTopic(String topic);

    /** 查找过期时间早于入参的 ACTIVE 记忆（分页）。 */
    Page<MemoryRecord> findByStatusAndTtlExpireAtBefore(MemoryStatus status, Instant expireBefore, Pageable pageable);

    /** 按租户 + 状态计数。 */
    long countByTenantIdAndStatus(String tenantId, MemoryStatus status);

    /** 按内容 hash 查询（去重用）。 */
    Optional<MemoryRecord> findByContentHash(String contentHash);
}
