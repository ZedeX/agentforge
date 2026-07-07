package com.agent.quality.repository;

import com.agent.quality.entity.BadcaseRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * BadcaseRecord JPA Repository。
 *
 * <p>提供按 taskId 查询 Badcase 记录的能力。</p>
 */
@Repository
public interface BadcaseRecordRepository extends JpaRepository<BadcaseRecordEntity, Long> {

    /** 按任务 ID 查询所有 Badcase 记录。 */
    List<BadcaseRecordEntity> findByTaskId(String taskId);
}
