package com.agent.riskcontrol.repository;

import com.agent.riskcontrol.entity.ContentViolationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ContentViolation JPA Repository.
 */
@Repository
public interface ContentViolationRepository extends JpaRepository<ContentViolationEntity, Long> {

    /** Find by violation ID. */
    Optional<ContentViolationEntity> findByViolationId(String violationId);
}
