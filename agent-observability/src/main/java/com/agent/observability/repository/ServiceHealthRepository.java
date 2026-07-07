package com.agent.observability.repository;

import com.agent.observability.entity.ServiceHealthEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA Repository for {@link ServiceHealthEntity}.
 */
@Repository
public interface ServiceHealthRepository extends JpaRepository<ServiceHealthEntity, Long> {

    /** Find by service name. */
    Optional<ServiceHealthEntity> findByServiceName(String serviceName);
}
