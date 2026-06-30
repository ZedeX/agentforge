package com.agent.modelgateway.repository;

import com.agent.modelgateway.model.ModelUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link ModelUsageLog} (Plan 07 T12).
 *
 * <p>Supports usage log lookup by trace/tenant and tenant-level cost aggregation
 * for quota enforcement and billing reports.</p>
 */
@Repository
public interface ModelUsageLogRepository extends JpaRepository<ModelUsageLog, Long> {

    /** Find all usage logs for a given trace id (for request tracing). */
    List<ModelUsageLog> findByTraceId(String traceId);

    /** Find all usage logs for a tenant within a time range (epoch millis). */
    List<ModelUsageLog> findByTenantIdAndCreatedAtBetween(String tenantId, long startMillis, long endMillis);

    /** Aggregate total cost (USD) for a tenant within a time range. */
    @Query("SELECT COALESCE(SUM(u.totalCostUsd), 0.0) FROM ModelUsageLog u " +
           "WHERE u.tenantId = :tenantId AND u.createdAt >= :startMillis AND u.createdAt < :endMillis")
    double sumTotalCostByTenantAndDateRange(@Param("tenantId") String tenantId,
                                            @Param("startMillis") long startMillis,
                                            @Param("endMillis") long endMillis);

    /** Count usage logs for a tenant within a time range (for rate limiting). */
    long countByTenantIdAndCreatedAtBetween(String tenantId, long startMillis, long endMillis);
}
