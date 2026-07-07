package com.agent.observability.repository;

import com.agent.observability.entity.TraceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA Repository for {@link TraceEntity}.
 */
@Repository
public interface TraceRepository extends JpaRepository<TraceEntity, Long> {

    /** Find by trace ID. */
    Optional<TraceEntity> findByTraceId(String traceId);

    /** Find by root service and time range. */
    List<TraceEntity> findByRootServiceAndStartTimeBetween(
            String rootService, Instant start, Instant end);

    /** Find by time range. */
    List<TraceEntity> findByStartTimeBetween(Instant start, Instant end);
}
