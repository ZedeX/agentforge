package com.agent.hallucination.repository;

import com.agent.hallucination.entity.HallucinationEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA Repository for {@link HallucinationEventEntity}.
 */
@Repository
public interface HallucinationEventRepository extends JpaRepository<HallucinationEventEntity, Long> {

    /** Find by business event ID. */
    Optional<HallucinationEventEntity> findByEventId(String eventId);
}
