package com.agent.modelgateway.repository;

import com.agent.modelgateway.model.ModelProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link ModelProvider} (Plan 07 T2).
 *
 * <p>Provides provider lookup by code and enabled status for ModelRouter / CostMeter.</p>
 */
@Repository
public interface ModelProviderRepository extends JpaRepository<ModelProvider, Long> {

    /** Find a provider by its unique code (e.g. "openai", "anthropic"). */
    Optional<ModelProvider> findByProviderCode(String providerCode);

    /** Find all enabled providers (for routing candidate enumeration). */
    List<ModelProvider> findByEnabledTrue();

    /** Check existence by provider code (for registration idempotency). */
    boolean existsByProviderCode(String providerCode);
}
