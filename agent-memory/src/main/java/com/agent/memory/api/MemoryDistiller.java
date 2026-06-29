package com.agent.memory.api;

import com.agent.memory.model.MemoryTopic;

/**
 * Memory distiller port (F12.D7: same-topic fragments distillation).
 */
public interface MemoryDistiller {

    /**
     * Distill a topic: generate summary + archive originals.
     *
     * <p>Triggered when same-topic fragments >= 5.
     * Compression ratio should be > 0.5 (50%).</p>
     *
     * @return distilled topic with summary + compression ratio.
     */
    MemoryTopic distill(MemoryTopic topic);
}
