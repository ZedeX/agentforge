package com.agent.observability.repository;

import com.agent.observability.entity.MetricDataPointEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * JPA Repository for {@link MetricDataPointEntity}.
 */
@Repository
public interface MetricDataPointRepository extends JpaRepository<MetricDataPointEntity, Long> {

    /** Find by service name and metric name and time range. */
    List<MetricDataPointEntity> findByServiceNameAndMetricNameAndTimestampBetween(
            String serviceName, String metricName, Instant start, Instant end);

    /** Find by service name and time range. */
    List<MetricDataPointEntity> findByServiceNameAndTimestampBetween(
            String serviceName, Instant start, Instant end);

    /** Find by time range. */
    List<MetricDataPointEntity> findByTimestampBetween(Instant start, Instant end);
}
