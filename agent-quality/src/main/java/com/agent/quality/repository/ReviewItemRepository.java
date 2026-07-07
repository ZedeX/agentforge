package com.agent.quality.repository;

import com.agent.quality.entity.ReviewItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ReviewItem JPA Repository。
 *
 * <p>提供按状态查询审核条目的能力。</p>
 */
@Repository
public interface ReviewItemRepository extends JpaRepository<ReviewItemEntity, Long> {

    /** 按审核状态查询所有条目。 */
    List<ReviewItemEntity> findByStatus(String status);

    /** 按审核状态查询，按入队时间升序排列（FIFO）。 */
    List<ReviewItemEntity> findByStatusOrderByEnqueuedAtAsc(String status);
}
