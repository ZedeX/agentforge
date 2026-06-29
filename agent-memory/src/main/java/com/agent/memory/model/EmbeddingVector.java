package com.agent.memory.model;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Embedding vector (F12.D5: write-time vectorization).
 *
 * <p>Default dimension = 1024 (bge-large-zh).</p>
 */
public class EmbeddingVector implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_DIM = 1024;

    private float[] values;
    private int dim;

    public EmbeddingVector() {
    }

    public EmbeddingVector(float[] values) {
        this.values = values;
        this.dim = values == null ? 0 : values.length;
    }

    public float[] getValues() {
        return values;
    }

    public void setValues(float[] values) {
        this.values = values;
        this.dim = values == null ? 0 : values.length;
    }

    public int getDim() {
        return dim;
    }

    public void setDim(int dim) {
        this.dim = dim;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmbeddingVector)) return false;
        EmbeddingVector that = (EmbeddingVector) o;
        return Arrays.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }
}
