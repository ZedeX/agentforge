package com.agent.drift.repository;

import com.agent.drift.entity.DriftSignalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA Repository for {@link DriftSignalEntity}.
 */
@Repository
public interface DriftSignalRepository extends JpaRepository<DriftSignalEntity, Long> {

    /** Find by business signal ID. */
    Optional<DriftSignalEntity> findBySignalId(String signalId);

    /** Find signals by agent ID ordered by detection time descending. */
    List<DriftSignalEntity> findByAgentIdOrderByDetectedAtDesc(String agentId);
}
