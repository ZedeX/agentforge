package com.agent.repo.repository;

import com.agent.repo.model.AgentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link AgentVersion} (Plan 08 T3).
 *
 * <p>Provides version history lookup ordered by version desc, latest version
 * retrieval, and existence checks for (agentId, version) unique pair.</p>
 */
@Repository
public interface AgentVersionRepository extends JpaRepository<AgentVersion, Long> {

    List<AgentVersion> findByAgentIdOrderByVersionDesc(String agentId);

    Optional<AgentVersion> findTopByAgentIdOrderByVersionDesc(String agentId);

    Optional<AgentVersion> findByAgentIdAndVersion(String agentId, int version);

    boolean existsByAgentIdAndVersion(String agentId, int version);

    long countByAgentId(String agentId);
}
