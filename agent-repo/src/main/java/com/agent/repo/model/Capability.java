package com.agent.repo.model;

import com.agent.repo.enums.CapabilityTag;

/**
 * Agent capability descriptor (doc 06-agent-repo §3.1).
 *
 * <p>Registered with CapabilityRegistry so Agents can be discovered by capability tag.
 * Skeleton stage: in-memory POJO. JPA Entity deferred to Plan 08 deepening.</p>
 */
public class Capability {

    private String code;
    private String name;
    private CapabilityTag tag;
    private String description;
    private boolean enabled = true;

    public Capability() {
    }

    public Capability(String code, String name, CapabilityTag tag) {
        this.code = code;
        this.name = name;
        this.tag = tag;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public CapabilityTag getTag() { return tag; }
    public void setTag(CapabilityTag tag) { this.tag = tag; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
