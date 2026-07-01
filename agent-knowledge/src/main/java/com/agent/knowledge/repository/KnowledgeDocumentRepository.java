package com.agent.knowledge.repository;

import com.agent.knowledge.model.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link KnowledgeDocument} (Plan 08 T8).
 *
 * <p>Provides lookup by business key docId, kbId-based filtering for cascade
 * queries, existence checks and bulk delete by kbId.</p>
 */
@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    Optional<KnowledgeDocument> findByDocId(String docId);

    boolean existsByDocId(String docId);

    List<KnowledgeDocument> findByKbId(String kbId);

    List<KnowledgeDocument> findByKbIdOrderByCreatedAtDesc(String kbId);

    long deleteByKbId(String kbId);
}
