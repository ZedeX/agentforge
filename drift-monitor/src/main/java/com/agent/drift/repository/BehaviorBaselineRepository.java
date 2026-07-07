package com.agent.drift.repository;

import com.agent.drift.entity.BehaviorBaselineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA Repository for {@link BehaviorBaselineEntity}.
 */
@Repository
public interface BehaviorBaselineRepository extends JpaRepository<BehaviorBaselineEntity, Long> {

    /** Find baseline by agent ID and baseline type. */
    Optional<BehaviorBaselineEntity> findByAgentIdAndBaselineType(String agentId, String baselineType);

    /** Find all baselines for an agent. */
    List<BehaviorBaselineEntity> findByAgentId(String agentId);
}
