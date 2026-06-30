package com.agent.knowledge.api.impl;

import com.agent.knowledge.api.KnowledgeService;
import com.agent.knowledge.enums.KnowledgeStatus;
import com.agent.knowledge.model.KnowledgeBase;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory knowledge base service (doc 07-knowledge §5).
 *
 * <p>Skeleton stage: maintains KB metadata in a ConcurrentHashMap. Soft-delete sets status=DELETED.
 * JPA + gRPC deferred to Plan 08 T11.</p>
 */
@Component
public class KnowledgeServiceImpl implements KnowledgeService {

    private final Map<String, KnowledgeBase> bases = new ConcurrentHashMap<>();

    @Override
    public KnowledgeBase createBase(String kbId, String name, String description) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (kbId == null || kbId.isEmpty()) {
            kbId = "kb-" + UUID.randomUUID().toString().substring(0, 8);
        }
        if (bases.containsKey(kbId)) {
            return null;
        }
        KnowledgeBase base = new KnowledgeBase(kbId, name);
        base.setDescription(description);
        base.setStatus(KnowledgeStatus.CREATING);
        long now = System.currentTimeMillis();
        base.setCreatedAt(now);
        base.setUpdatedAt(now);
        bases.put(kbId, base);
        return base;
    }

    @Override
    public KnowledgeBase getBase(String kbId) {
        if (kbId == null) {
            return null;
        }
        KnowledgeBase base = bases.get(kbId);
        if (base == null || base.getStatus() == KnowledgeStatus.DELETED) {
            return null;
        }
        return base;
    }

    @Override
    public boolean deleteBase(String kbId) {
        if (kbId == null) {
            return false;
        }
        KnowledgeBase base = bases.get(kbId);
        if (base == null || base.getStatus() == KnowledgeStatus.DELETED) {
            return false;
        }
        base.setStatus(KnowledgeStatus.DELETED);
        base.setUpdatedAt(System.currentTimeMillis());
        return true;
    }

    @Override
    public List<KnowledgeBase> listBases(KnowledgeStatus status) {
        if (status == null) {
            return bases.values().stream()
                    .filter(b -> b.getStatus() != KnowledgeStatus.DELETED)
                    .collect(Collectors.toList());
        }
        return bases.values().stream()
                .filter(b -> b.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public KnowledgeBase updateStatus(String kbId, String statusCode) {
        if (kbId == null) {
            return null;
        }
        KnowledgeBase base = bases.get(kbId);
        if (base == null || base.getStatus() == KnowledgeStatus.DELETED) {
            return null;
        }
        KnowledgeStatus newStatus = KnowledgeStatus.fromCode(statusCode);
        base.setStatus(newStatus);
        base.setUpdatedAt(System.currentTimeMillis());
        return base;
    }

    /** Expose internal KB map for cross-component wiring (skeleton only). */
    public Map<String, KnowledgeBase> getInternalStore() {
        return bases;
    }
}
