package com.agent.knowledge.repository;

import com.agent.knowledge.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link DocumentChunk} (Plan 08 T8).
 *
 * <p>Provides lookup by business key chunkId, kbId+docId combined query for
 * per-document chunk enumeration, count by kbId and bulk delete for idempotent
 * re-ingest (deleteByKbIdAndDocId).</p>
 */
@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    Optional<DocumentChunk> findByChunkId(String chunkId);

    boolean existsByChunkId(String chunkId);

    List<DocumentChunk> findByKbIdAndDocId(String kbId, String docId);

    List<DocumentChunk> findByKbId(String kbId);

    List<DocumentChunk> findByDocId(String docId);

    long countByKbId(String kbId);

    long deleteByKbIdAndDocId(String kbId, String docId);

    long deleteByKbId(String kbId);
}
