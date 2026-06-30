package com.agent.repo.repository;

import com.agent.repo.enums.AgentStatus;
import com.agent.repo.enums.AgentTier;
import com.agent.repo.model.AgentDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link AgentDefinition} (Plan 08 T2).
 *
 * <p>Provides lookup by business key agentId, status state-machine filtering,
 * tier-based queries and existence checks.</p>
 */
@Repository
public interface AgentDefinitionRepository extends JpaRepository<AgentDefinition, Long> {

    Optional<AgentDefinition> findByAgentId(String agentId);

    boolean existsByAgentId(String agentId);

    List<AgentDefinition> findByStatus(AgentStatus status);

    List<AgentDefinition> findByAgentTier(AgentTier tier);

    List<AgentDefinition> findByStatusAndAgentTier(AgentStatus status, AgentTier tier);

    long deleteByAgentId(String agentId);

    List<AgentDefinition> findByStatusOrderByCreatedAtDesc(AgentStatus status);
}
