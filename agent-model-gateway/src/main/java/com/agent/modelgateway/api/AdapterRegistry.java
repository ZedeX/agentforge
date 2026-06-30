package com.agent.modelgateway.api;

/**
 * Adapter registry (doc 02-api §5 ModelProviderAdapter dispatch).
 *
 * <p>Maps providerCode → ModelProviderAdapter instance. Supports dynamic registration.</p>
 */
public interface AdapterRegistry {

    /**
     * Register an adapter.
     */
    void register(ModelProviderAdapter adapter);

    /**
     * Look up adapter by provider code.
     *
     * @return adapter instance, null if not registered
     */
    ModelProviderAdapter get(String providerCode);

    /**
     * Check if an adapter is registered for the given provider code.
     */
    boolean has(String providerCode);
}
