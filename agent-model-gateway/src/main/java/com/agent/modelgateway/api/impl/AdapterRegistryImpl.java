package com.agent.modelgateway.api.impl;

import com.agent.modelgateway.api.AdapterRegistry;
import com.agent.modelgateway.api.ModelProviderAdapter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory adapter registry.
 */
@Component
public class AdapterRegistryImpl implements AdapterRegistry {

    private final Map<String, ModelProviderAdapter> adapters = new ConcurrentHashMap<>();

    @Override
    public void register(ModelProviderAdapter adapter) {
        if (adapter == null || adapter.getProviderCode() == null) {
            return;
        }
        adapters.put(adapter.getProviderCode(), adapter);
    }

    @Override
    public ModelProviderAdapter get(String providerCode) {
        if (providerCode == null) {
            return null;
        }
        return adapters.get(providerCode);
    }

    @Override
    public boolean has(String providerCode) {
        return providerCode != null && adapters.containsKey(providerCode);
    }
}
