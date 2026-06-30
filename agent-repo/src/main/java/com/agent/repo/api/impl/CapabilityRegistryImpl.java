package com.agent.repo.api.impl;

import com.agent.repo.api.CapabilityRegistry;
import com.agent.repo.enums.CapabilityTag;
import com.agent.repo.model.Capability;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory capability registry (doc 06-agent-repo §3.1).
 *
 * <p>Skeleton stage: ConcurrentHashMap keyed by capability code.</p>
 */
@Component
public class CapabilityRegistryImpl implements CapabilityRegistry {

    private final Map<String, Capability> store = new ConcurrentHashMap<>();

    public CapabilityRegistryImpl() {
        // Seed default capabilities
        register(new Capability("code-gen", "代码生成", CapabilityTag.CODE_GENERATION));
        register(new Capability("code-review", "代码审查", CapabilityTag.CODE_REVIEW));
        register(new Capability("translate", "翻译", CapabilityTag.TRANSLATION));
    }

    @Override
    public void register(Capability capability) {
        if (capability == null || capability.getCode() == null) {
            return;
        }
        store.put(capability.getCode(), capability);
    }

    @Override
    public Optional<Capability> find(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(code));
    }

    @Override
    public List<Capability> list() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<Capability> findByTag(CapabilityTag tag) {
        if (tag == null) {
            return new ArrayList<>();
        }
        List<Capability> result = new ArrayList<>();
        for (Capability c : store.values()) {
            if (tag.equals(c.getTag())) {
                result.add(c);
            }
        }
        return result;
    }

    @Override
    public boolean remove(String code) {
        if (code == null) {
            return false;
        }
        return store.remove(code) != null;
    }

    @Override
    public boolean exists(String code) {
        return code != null && store.containsKey(code);
    }
}
