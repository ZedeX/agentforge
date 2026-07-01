package com.agent.knowledge.repository;

import com.agent.knowledge.model.KnowledgeVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link KnowledgeVersion} (Plan 08 T8).
 *
 * <p>Provides lookup by business key versionId, kbId-based version history
 * listing (descending), latest version probe and count for FIFO eviction.</p>
 */
@Repository
public interface KnowledgeVersionRepository extends JpaRepository<KnowledgeVersion, Long> {

    Optional<KnowledgeVersion> findByVersionId(String versionId);

    boolean existsByVersionId(String versionId);

    List<KnowledgeVersion> findByKbIdOrderByVersionDesc(String kbId);

    Optional<KnowledgeVersion> findTopByKbIdOrderByVersionDesc(String kbId);

    long countByKbId(String kbId);

    long deleteByKbId(String kbId);
}
