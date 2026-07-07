package com.agent.hallucination.repository;

import com.agent.hallucination.entity.HallucinationMetricEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * HallucinationMetricEntity JPA Repository（F10 L6 指标落库查询）。
 */
@Repository
public interface HallucinationMetricRepository extends JpaRepository<HallucinationMetricEntity, Long> {

    /** 按业务 ID 查询。 */
    Optional<HallucinationMetricEntity> findByMetricId(String metricId);

    /** 按租户 + Agent + 统计日期查询。 */
    Optional<HallucinationMetricEntity> findByTenantIdAndAgentIdAndStatDate(
            String tenantId, String agentId, LocalDate statDate);

    /** 按租户 + Agent 查询指标列表（按日期降序）。 */
    List<HallucinationMetricEntity> findByTenantIdAndAgentIdOrderByStatDateDesc(
            String tenantId, String agentId);

    /** 按统计日期范围查询。 */
    List<HallucinationMetricEntity> findByStatDateBetweenOrderByStatDateDesc(
            LocalDate startDate, LocalDate endDate);
}
