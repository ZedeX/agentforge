package com.agent.knowledge.api.impl;

import com.agent.knowledge.api.VersionManager;
import com.agent.knowledge.model.KnowledgeBase;
import com.agent.knowledge.model.KnowledgeVersion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory version manager (doc 06-agent-repo §4.2 version pattern applied to KB).
 *
 * <p>Skeleton stage: maintains KB version snapshots in a ConcurrentHashMap.
 * JPA + Flyway deferred to Plan 08 T8.</p>
 */
@Component
public class VersionManagerImpl implements VersionManager {

    private final Map<String, KnowledgeBase> boundBases = new ConcurrentHashMap<>();
    private final Map<String, List<KnowledgeVersion>> versionHistory = new ConcurrentHashMap<>();

    @Override
    public KnowledgeVersion snapshot(String kbId, String changeLog) {
        if (kbId == null) {
            return null;
        }
        KnowledgeBase base = boundBases.get(kbId);
        if (base == null) {
            return null;
        }
        List<KnowledgeVersion> versions = versionHistory.computeIfAbsent(kbId, k -> new ArrayList<>());
        int newVersion = versions.size() + 1;
        String snapshot = serializeSnapshot(base);
        KnowledgeVersion version = new KnowledgeVersion(
                UUID.randomUUID().toString(),
                kbId,
                newVersion,
                snapshot,
                changeLog != null ? changeLog : "",
                System.currentTimeMillis()
        );
        versions.add(version);
        return version;
    }

    @Override
    public List<KnowledgeVersion> listVersions(String kbId) {
        if (kbId == null) {
            return new ArrayList<>();
        }
        List<KnowledgeVersion> versions = versionHistory.get(kbId);
        if (versions == null) {
            return new ArrayList<>();
        }
        return versions.stream()
                .sorted(Comparator.comparingInt(KnowledgeVersion::getVersion).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public KnowledgeVersion getVersion(String kbId, int version) {
        if (kbId == null || version <= 0) {
            return null;
        }
        List<KnowledgeVersion> versions = versionHistory.get(kbId);
        if (versions == null) {
            return null;
        }
        for (KnowledgeVersion v : versions) {
            if (v.getVersion() == version) {
                return v;
            }
        }
        return null;
    }

    @Override
    public KnowledgeVersion rollback(String kbId, int version) {
        KnowledgeVersion target = getVersion(kbId, version);
        if (target == null) {
            return null;
        }
        KnowledgeBase base = boundBases.get(kbId);
        if (base == null) {
            return null;
        }
        // Skeleton: restore metadata fields from snapshot (mock: just update name)
        deserializeSnapshot(target.getSnapshot(), base);
        base.setUpdatedAt(System.currentTimeMillis());
        return target;
    }

    @Override
    public void bindBase(KnowledgeBase base) {
        if (base == null || base.getKbId() == null) {
            return;
        }
        boundBases.put(base.getKbId(), base);
    }

    private String serializeSnapshot(KnowledgeBase base) {
        return String.format("{\"kbId\":\"%s\",\"name\":\"%s\",\"dimension\":%d,\"status\":\"%s\"}",
                base.getKbId(),
                base.getName() != null ? base.getName() : "",
                base.getDimension(),
                base.getStatus() != null ? base.getStatus().getCode() : "ready");
    }

    private void deserializeSnapshot(String snapshot, KnowledgeBase base) {
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        // Skeleton: extract name from JSON-like string
        // Pattern: "name":"<value>"  (8 chars to skip past "name":" to reach value start)
        int nameStart = snapshot.indexOf("\"name\":\"");
        if (nameStart >= 0) {
            nameStart += 8;
            int nameEnd = snapshot.indexOf("\"", nameStart);
            if (nameEnd > nameStart) {
                base.setName(snapshot.substring(nameStart, nameEnd));
            }
        }
    }
}
