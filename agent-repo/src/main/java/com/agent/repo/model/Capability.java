package com.agent.repo.model;

import com.agent.repo.enums.CapabilityTag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Agent capability descriptor (doc 06-agent-repo §3.1, Plan 08 T4).
 *
 * <p>JPA Entity backing capability table. Registered with CapabilityRegistry so Agents
 * can be discovered by capability tag. Uses natural key {@code code} as primary key.</p>
 */
@Entity
@Table(name = "capability")
public class Capability {

    @Id
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag", nullable = false, length = 32)
    private CapabilityTag tag;

    @Column(name = "description", length = 65535)
    private String description;

    @Column(name = "enabled", nullable = false)
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
