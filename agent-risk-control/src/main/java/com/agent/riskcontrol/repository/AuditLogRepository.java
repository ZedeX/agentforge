package com.agent.riskcontrol.repository;

import com.agent.riskcontrol.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * AuditLog JPA Repository.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    /** Find by audit ID. */
    Optional<AuditLogEntity> findByAuditId(String auditId);
}
