package com.agent.repo.api.impl;

import com.agent.repo.api.VersionControl;
import com.agent.repo.model.AgentDefinition;
import com.agent.repo.model.AgentVersion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory version control (doc 06-agent-repo §4.2).
 *
 * <p>Skeleton stage: maintains version snapshots in a ConcurrentHashMap keyed by agentId.
 * Each agent's versions are stored in a LinkedHashMap (ordered by insertion = version order).
 * FIFO eviction when count exceeds {@link #maxHistory}.</p>
 */
@Component
public class VersionControlImpl implements VersionControl {

    /** Max history versions retained per agent (doc 06 §4.2: 20). */
    static final int DEFAULT_MAX_HISTORY = 20;

    private final int maxHistory;
    private final Map<String, Map<Integer, AgentVersion>> store = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(0);

    public VersionControlImpl() {
        this(DEFAULT_MAX_HISTORY);
    }

    /** Constructor for tests to override maxHistory. */
    public VersionControlImpl(int maxHistory) {
        this.maxHistory = maxHistory;
    }

    @Override
    public AgentVersion snapshot(AgentDefinition agent, String changeLog) {
        if (agent == null || agent.getAgentId() == null) {
            throw new IllegalArgumentException("agent or agentId must not be null");
        }
        if (agent.getVersion() <= 0) {
            throw new IllegalArgumentException("agent.version must be positive");
        }
        Map<Integer, AgentVersion> versions = store.computeIfAbsent(agent.getAgentId(), k -> new LinkedHashMap<>());
        // If snapshot for this version already exists, treat as idempotent and return existing
        AgentVersion existing = versions.get(agent.getVersion());
        if (existing != null) {
            return existing;
        }
        AgentVersion version = new AgentVersion(
                agent.getAgentId(),
                agent.getVersion(),
                serialize(agent),
                changeLog != null ? changeLog : ""
        );
        version.setId(idSeq.incrementAndGet());
        version.setCreatedAt(System.currentTimeMillis());
        versions.put(agent.getVersion(), version);
        // FIFO eviction: remove oldest if exceeds maxHistory
        while (versions.size() > maxHistory) {
            Integer oldestKey = versions.keySet().iterator().next();
            versions.remove(oldestKey);
        }
        return version;
    }

    @Override
    public List<AgentVersion> listVersions(String agentId) {
        if (agentId == null) {
            return new ArrayList<>();
        }
        Map<Integer, AgentVersion> versions = store.get(agentId);
        if (versions == null) {
            return new ArrayList<>();
        }
        List<AgentVersion> result = new ArrayList<>(versions.values());
        result.sort(Comparator.comparingInt(AgentVersion::getVersion).reversed());
        return result;
    }

    @Override
    public Optional<AgentVersion> getVersion(String agentId, int version) {
        if (agentId == null) {
            return Optional.empty();
        }
        Map<Integer, AgentVersion> versions = store.get(agentId);
        if (versions == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(versions.get(version));
    }

    @Override
    public Optional<AgentDefinition> rollback(AgentDefinition agent, int version) {
        if (agent == null || agent.getAgentId() == null) {
            return Optional.empty();
        }
        Map<Integer, AgentVersion> versions = store.get(agent.getAgentId());
        if (versions == null) {
            return Optional.empty();
        }
        AgentVersion target = versions.get(version);
        if (target == null) {
            return Optional.empty();
        }
        // Restore from snapshot: return new AgentDefinition with restored fields
        AgentDefinition restored = deserialize(target.getSnapshot(), agent);
        return Optional.of(restored);
    }

    @Override
    public int countVersions(String agentId) {
        if (agentId == null) {
            return 0;
        }
        Map<Integer, AgentVersion> versions = store.get(agentId);
        return versions != null ? versions.size() : 0;
    }

    /**
     * Serialize AgentDefinition to a JSON-like string (skeleton: simplified key=value format).
     * Real implementation will use Jackson ObjectMapper (Plan 08 T3 refactor).
     */
    private String serialize(AgentDefinition agent) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"agentId\":\"").append(nullSafe(agent.getAgentId())).append("\"");
        sb.append(",\"name\":\"").append(nullSafe(agent.getName())).append("\"");
        sb.append(",\"description\":\"").append(nullSafe(agent.getDescription())).append("\"");
        sb.append(",\"systemPrompt\":\"").append(nullSafe(agent.getSystemPrompt())).append("\"");
        sb.append(",\"agentTier\":\"").append(agent.getAgentTier() != null ? agent.getAgentTier().getCode() : "").append("\"");
        sb.append(",\"status\":\"").append(agent.getStatus() != null ? agent.getStatus().getCode() : "").append("\"");
        sb.append(",\"version\":").append(agent.getVersion());
        sb.append(",\"maxSteps\":").append(agent.getMaxSteps());
        sb.append(",\"maxToken\":").append(agent.getMaxToken());
        sb.append("}");
        return sb.toString();
    }

    /**
     * Deserialize snapshot JSON back to AgentDefinition. Skeleton: restore key fields.
     */
    private AgentDefinition deserialize(String snapshot, AgentDefinition fallback) {
        AgentDefinition restored = new AgentDefinition();
        restored.setAgentId(fallback.getAgentId());
        // Parse name
        restored.setName(extractStringField(snapshot, "name", fallback.getName()));
        restored.setDescription(extractStringField(snapshot, "description", fallback.getDescription()));
        restored.setSystemPrompt(extractStringField(snapshot, "systemPrompt", fallback.getSystemPrompt()));
        // Tier / status restore from code
        String tierCode = extractStringField(snapshot, "agentTier", "");
        restored.setAgentTier(com.agent.repo.enums.AgentTier.fromCode(tierCode));
        String statusCode = extractStringField(snapshot, "status", "");
        restored.setStatus(com.agent.repo.enums.AgentStatus.fromCode(statusCode));
        // Restore version + steps + token
        restored.setVersion(extractIntField(snapshot, "version", fallback.getVersion()));
        restored.setMaxSteps(extractIntField(snapshot, "maxSteps", fallback.getMaxSteps()));
        restored.setMaxToken(extractIntField(snapshot, "maxToken", fallback.getMaxToken()));
        // Preserve binding lists + timestamps from current agent (not snapshotted in skeleton)
        restored.setBoundTools(fallback.getBoundTools());
        restored.setBoundKnowledgeIds(fallback.getBoundKnowledgeIds());
        restored.setCreatedAt(fallback.getCreatedAt());
        restored.setUpdatedAt(System.currentTimeMillis());
        return restored;
    }

    private String extractStringField(String json, String field, String fallback) {
        if (json == null) {
            return fallback;
        }
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            return fallback;
        }
        start += marker.length();
        int end = json.indexOf("\"", start);
        if (end < 0) {
            return fallback;
        }
        return json.substring(start, end);
    }

    private int extractIntField(String json, String field, int fallback) {
        if (json == null) {
            return fallback;
        }
        String marker = "\"" + field + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            return fallback;
        }
        start += marker.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        if (end == start) {
            return fallback;
        }
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }
}
