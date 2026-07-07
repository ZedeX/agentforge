package com.agent.memory.model;

/**
 * S-04: Payload for the "memory.vector.insert" outbox topic.
 *
 * <p>Carries the data needed to insert a memory record's embedding vector
 * into the vector store. Serialized as JSON in the outbox message.</p>
 *
 * @see com.agent.memory.outbox.MemoryVectorInsertOutboxConsumer
 */
public class VectorInsertPayload {

    private String memoryId;
    private String tenantId;
    private float[] embeddingValues;
    private int embeddingDim;

    public VectorInsertPayload() {
    }

    public VectorInsertPayload(String memoryId, String tenantId,
                               float[] embeddingValues, int embeddingDim) {
        this.memoryId = memoryId;
        this.tenantId = tenantId;
        this.embeddingValues = embeddingValues;
        this.embeddingDim = embeddingDim;
    }

    public String getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public float[] getEmbeddingValues() {
        return embeddingValues;
    }

    public void setEmbeddingValues(float[] embeddingValues) {
        this.embeddingValues = embeddingValues;
    }

    public int getEmbeddingDim() {
        return embeddingDim;
    }

    public void setEmbeddingDim(int embeddingDim) {
        this.embeddingDim = embeddingDim;
    }
}
