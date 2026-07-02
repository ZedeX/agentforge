package com.agent.memory.repository;

import com.agent.memory.model.MemoryExtractLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MemoryExtractLog JPA Repository (Plan 03 T2).
 */
@Repository
public interface MemoryExtractLogRepository extends JpaRepository<MemoryExtractLog, Long> {

    /** 按来源任务 ID 查询。 */
    List<MemoryExtractLog> findByTaskId(String taskId);
}
