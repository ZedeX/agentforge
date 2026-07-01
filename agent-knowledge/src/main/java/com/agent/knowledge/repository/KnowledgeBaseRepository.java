package com.agent.knowledge.repository;

import com.agent.knowledge.enums.KnowledgeStatus;
import com.agent.knowledge.model.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link KnowledgeBase} (Plan 08 T8).
 *
 * <p>Provides lookup by business key kbId, status state-machine filtering,
 * existence checks and bulk delete by kbId.</p>
 */
@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    Optional<KnowledgeBase> findByKbId(String kbId);

    boolean existsByKbId(String kbId);

    List<KnowledgeBase> findByStatus(KnowledgeStatus status);

    List<KnowledgeBase> findByStatusOrderByCreatedAtDesc(KnowledgeStatus status);

    long deleteByKbId(String kbId);
}
