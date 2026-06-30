package com.agent.knowledge.api;

import com.agent.knowledge.enums.KnowledgeStatus;
import com.agent.knowledge.model.KnowledgeBase;

import java.util.List;

/**
 * Knowledge base CRUD service (doc 07-knowledge §5, PRD §二(二) knowledge base management).
 *
 * <p>Manages knowledge base metadata lifecycle. Skeleton stage: in-memory.
 * JPA + gRPC deferred to Plan 08 T11.</p>
 */
public interface KnowledgeService {

    /**
     * Create a new knowledge base.
     *
     * @param kbId        unique KB identifier, null/empty for auto-generate
     * @param name        KB display name
     * @param description KB description, nullable
     * @return created KnowledgeBase with status=CREATING
     */
    KnowledgeBase createBase(String kbId, String name, String description);

    /**
     * Get knowledge base by id.
     *
     * @return KnowledgeBase, null if not found or deleted
     */
    KnowledgeBase getBase(String kbId);

    /**
     * Soft-delete knowledge base (sets status=DELETED).
     *
     * @return true if deleted, false if not found or already deleted
     */
    boolean deleteBase(String kbId);

    /**
     * List knowledge bases filtered by status.
     *
     * @param status filter, null for all (excludes DELETED)
     * @return list of matching KnowledgeBase
     */
    List<KnowledgeBase> listBases(KnowledgeStatus status);

    /**
     * Update KB status by code string (parses via KnowledgeStatus.fromCode).
     *
     * @return updated KnowledgeBase, null if not found
     */
    KnowledgeBase updateStatus(String kbId, String statusCode);
}
