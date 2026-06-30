package com.agent.repo.repository;

import com.agent.repo.enums.CapabilityTag;
import com.agent.repo.model.Capability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Capability} (Plan 08 T4).
 *
 * <p>Capability uses natural key {@code code} as {@code @Id}. Provides tag-based
 * filtering, enabled-only lookups and existence checks by code.</p>
 */
@Repository
public interface CapabilityRepository extends JpaRepository<Capability, String> {

    List<Capability> findByTag(CapabilityTag tag);

    List<Capability> findByEnabledTrue();

    List<Capability> findByTagAndEnabledTrue(CapabilityTag tag);

    boolean existsByCode(String code);
}
